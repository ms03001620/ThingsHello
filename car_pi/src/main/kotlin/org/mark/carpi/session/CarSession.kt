package org.mark.carpi.session

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mark.carpi.model.Command
import org.mark.carpi.model.ReceivedCommand
import org.mark.carpi.repository.ActionLog
import org.mark.carpi.repository.CarRepository
import org.mark.carpi.usecase.BeepUseCase
import org.mark.carpi.usecase.DriveWheelUseCase
import org.mark.carpi.usecase.MotionLock
import org.mark.carpi.usecase.MoveServoUseCase
import org.mark.carpi.usecase.RotateUseCase
import org.mark.carpi.usecase.SetLightUseCase

/**
 * 控制会话：所有指令与定时器（delay + Job.cancel）都在同一个单线程 [dispatcher] 上串行执行；
 * 分配指令 id、记录 command / rejected / unsupported.type，并把每个 Command 分发给对应 UseCase。
 * 只依赖 model / repository 端口 / usecase 与 kotlinx.coroutines（装配见 runtime）。
 *
 * 测试可传入 TestDispatcher：此时不创建任何线程，定时器由虚拟时钟驱动，可直接在测试线程调用 [handle]。
 */
class CarSession internal constructor(
    private val repository: CarRepository,
    private val log: ActionLog,
    dispatcher: CoroutineDispatcher?,
) : AutoCloseable {
    constructor(repository: CarRepository, log: ActionLog) : this(repository, log, null)

    /** 仅在未注入 dispatcher 时创建并由本会话关闭的专用线程。 */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val ownedThread: CloseableCoroutineDispatcher? = if (dispatcher == null) newSingleThreadContext("car-session") else null
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

    override fun close() {
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
        fun <T> of(repository: T): CarSession where T : CarRepository, T : ActionLog = CarSession(repository, repository)
    }
}
