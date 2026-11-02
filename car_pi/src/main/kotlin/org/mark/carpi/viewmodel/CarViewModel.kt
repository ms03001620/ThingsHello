package org.mark.carpi.viewmodel

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.mark.carpi.model.Command
import org.mark.carpi.model.ControlEvent
import org.mark.carpi.model.ReceivedCommand
import org.mark.carpi.repository.ActionLog
import org.mark.carpi.repository.CarRepository
import org.mark.carpi.repository.CommandRepository
import org.mark.carpi.usecase.BeepUseCase
import org.mark.carpi.usecase.DriveWheelUseCase
import org.mark.carpi.usecase.MotionLock
import org.mark.carpi.usecase.MoveServoUseCase
import org.mark.carpi.usecase.RotateUseCase
import org.mark.carpi.usecase.SetLightUseCase

/**
 * 控制会话：所有指令与定时器（delay + Job.cancel）都在同一个单线程 [dispatcher] 上串行执行；
 * 分配指令 id、记录 command / rejected / unsupported.type，并把每个 Command 分发给对应 UseCase。
 * 持有 [CommandRepository] 时由 [start] 收集控制端事件（类似 Android ViewModel 收集 Repository 的 Flow）。
 * 只依赖 model / repository / usecase 与 kotlinx.coroutines（装配见 runtime）。
 *
 * 测试可传入 TestDispatcher：此时不创建任何线程，定时器由虚拟时钟驱动，可直接在测试线程调用 [handle]。
 */
class CarViewModel internal constructor(
    private val repository: CarRepository,
    private val log: ActionLog,
    dispatcher: CoroutineDispatcher?,
    private val commands: CommandRepository? = null,
) : AutoCloseable {
    constructor(repository: CarRepository, log: ActionLog) : this(repository, log, null)
    constructor(repository: CarRepository, log: ActionLog, commands: CommandRepository) : this(repository, log, null, commands)

    /** 仅在未注入 dispatcher 时创建并由本会话关闭的专用线程。 */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val ownedThread: CloseableCoroutineDispatcher? = if (dispatcher == null) newSingleThreadContext("car-viewmodel") else null
    private val dispatcher: CoroutineDispatcher = dispatcher ?: ownedThread!!
    private val timers = CoroutineScope(
        SupervisorJob() + this.dispatcher + CoroutineExceptionHandler { _, e -> System.err.println("WARN timer: $e") }
    )
    private val motionLock = MotionLock(repository)
    private val driveWheel = DriveWheelUseCase(motionLock, repository)
    private val rotate = RotateUseCase(motionLock, repository)
    private val moveServo = MoveServoUseCase(motionLock, repository, timers)
    private val beepUseCase = BeepUseCase(repository, timers)
    private val setLight = SetLightUseCase(repository)
    private var sequence = 0L
    /** 收集 [commands] 的协程：读帧是阻塞 IO，跑在 Dispatchers.IO；处理切回 [dispatcher]。 */
    private val collector = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 开始收集控制端事件：命令交给 [handle]，断开时 [releaseControl]。 */
    fun start() {
        val source = commands ?: return
        collector.launch {
            source.events().collect { event ->
                when (event) {
                    ControlEvent.Connected -> Unit
                    is ControlEvent.Received -> try {
                        handle(event.command)
                    } catch (e: Exception) {
                        System.err.println("handle: $e")
                    }
                    ControlEvent.Disconnected -> try {
                        withTimeout(3_000) { releaseControl() }
                    } catch (e: Exception) {
                        System.err.println("disconnect stop: $e")
                    }
                }
            }
        }
    }

    /** 处理一帧并等待完成（调用方据此保证"处理完再读下一帧"）。 */
    suspend fun handle(received: ReceivedCommand) = withContext(dispatcher) {
        handle(received.type, received.body, received.command)
    }

    /** 每帧：先分配 id 并记录原始帧，再执行；任何 RuntimeException 记为 rejected。必须在 dispatcher（或测试线程）上调用。 */
    internal fun handle(type: Int, body: String, command: Command) {
        val id = ++sequence
        log.event(id, "command", mapOf("type" to type, "body" to body))
        if (command is Command.Invalid) {
            log.event(id, "rejected", command.reason)
            return
        }
        try {
            dispatch(id, command)
        } catch (e: RuntimeException) {
            log.event(id, "rejected", e.toString())
        }
    }

    private fun dispatch(id: Long, command: Command) {
        when (command) {
            is Command.Invalid -> Unit // 已在 handle 中处理
            is Command.Wheel -> driveWheel(id, command.left, command.right)
            is Command.Light -> setLight(id, command.on)
            is Command.Beep -> if (command.enabled) beepUseCase(id)
            is Command.Servo -> moveServo(id, command.progress)
            is Command.Rotate -> rotate(id, command.direction, command.speed)
            is Command.Unsupported -> log.event(id, "unsupported.type", command.type)
        }
    }

    /** 蜂鸣 ms 毫秒（如启动提示，id=0）；与手机指令同 dispatcher 串行，close() 会取消并经 all.stop 关闭蜂鸣器。 */
    suspend fun beep(ms: Int) = withContext(dispatcher) {
        try {
            beepUseCase(0, ms)
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    }

    /** Release motion on control disconnect; do not leave a physical motor running without its client. */
    suspend fun releaseControl() = withContext(dispatcher) { releaseControlNow() }

    internal fun releaseControlNow() {
        motionLock.cancelPendingServoRelease()
        motionLock.releaseOwner(0)
    }

    /** 关闭顺序：取消定时器 -> 释放 owner -> all.stop（之后停止定时器作用域与线程，最后 repository.close）。 */
    internal fun shutdownNow() {
        motionLock.cancelPendingServoRelease()
        beepUseCase.cancel()
        try {
            motionLock.releaseOwner(0)
        } finally {
            repository.allStop(0)
        }
    }

    /** 调用方应先关闭数据源（让阻塞读结束），这里等收集协程收尾后再停硬件。 */
    override fun close() {
        runBlocking { withTimeoutOrNull(5_000) { collector.coroutineContext.job.children.toList().joinAll() } }
        collector.cancel()
        try {
            runBlocking { withTimeout(5_000) { withContext(dispatcher) { shutdownNow() } } }
        } catch (e: Exception) {
            throw IllegalStateException("control shutdown", e)
        } finally {
            timers.cancel()
            ownedThread?.close()
            repository.close()
        }
    }

    companion object {
        /** 同一个对象既控制硬件又写动作日志（如 LoggingCarRepository）时，用它创建会话。 */
        fun <T> of(repository: T): CarViewModel where T : CarRepository, T : ActionLog = CarViewModel(repository, repository)

        /** 同上，并由 ViewModel 收集 [commands] 的控制端事件（见 [start]）。 */
        fun <T> of(repository: T, commands: CommandRepository): CarViewModel where T : CarRepository, T : ActionLog =
            CarViewModel(repository, repository, commands)
    }
}
