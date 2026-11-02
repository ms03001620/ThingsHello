package org.mark.carpi.session

import org.mark.carpi.model.Cancellable
import org.mark.carpi.model.Command
import org.mark.carpi.model.Scheduler
import org.mark.carpi.protocol.CommandParser
import org.mark.carpi.protocol.FrameCodec.Frame
import org.mark.carpi.repository.ActionLog
import org.mark.carpi.repository.CarRepository
import org.mark.carpi.usecase.BeepUseCase
import org.mark.carpi.usecase.DriveWheelUseCase
import org.mark.carpi.usecase.MotionLock
import org.mark.carpi.usecase.MoveServoUseCase
import org.mark.carpi.usecase.RotateUseCase
import org.mark.carpi.usecase.SetLightUseCase
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 控制会话：单线程 queue 串行执行指令与定时任务；分配指令 id、记录 command / rejected / unsupported.type，
 * 并把每个 Command 分发给对应 UseCase。只依赖 model / repository / usecase 类型（装配见 runtime）。
 *
 * [testScheduler] 仅供测试：传入后定时任务由它执行，测试可直接在本线程调用 [handle]。
 */
class CarSession internal constructor(
    private val repository: CarRepository,
    private val log: ActionLog,
    testScheduler: Scheduler?,
) : AutoCloseable {
    constructor(repository: CarRepository, log: ActionLog) : this(repository, log, null)

    private val queue: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val scheduler = testScheduler ?: Scheduler { delayMs, task ->
        val f = queue.schedule(Runnable { task() }, delayMs, TimeUnit.MILLISECONDS)
        Cancellable { f.cancel(false) }
    }
    private val motionLock = MotionLock(repository)
    private val driveWheel = DriveWheelUseCase(motionLock, repository)
    private val rotate = RotateUseCase(motionLock, repository)
    private val moveServo = MoveServoUseCase(motionLock, repository, scheduler)
    private val beepUseCase = BeepUseCase(repository, scheduler)
    private val setLight = SetLightUseCase(repository)
    private var sequence = 0L

    @Suppress("UNUSED_PARAMETER") // 协议目前没有回包；保留回调以便将来回复手机
    fun submit(frame: Frame, response: (Frame) -> Unit): Future<*> = queue.submit(Runnable {
        handle(frame.type, frame.body, CommandParser.parse(frame))
    })

    /** 每帧：先分配 id 并记录原始帧，再执行；任何 RuntimeException 记为 rejected。必须在 queue 线程（或测试线程）串行调用。 */
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

    /** 蜂鸣 ms 毫秒（如启动提示，id=0）；与手机指令同队列串行，close() 会取消并经 all.stop 关闭蜂鸣器。 */
    fun beep(ms: Int): Future<*> = queue.submit(Runnable {
        try {
            beepUseCase(0, ms)
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    })

    /** Release motion on control disconnect; do not leave a physical motor running without its client. */
    fun releaseControl(): Future<*> = queue.submit(Runnable { releaseControlNow() })

    internal fun releaseControlNow() {
        motionLock.cancelPendingServoRelease()
        motionLock.releaseOwner(0)
    }

    /** 关闭顺序：取消定时器 -> 释放 owner -> all.stop（之后停 queue，最后 repository.close）。 */
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
            queue.submit(Runnable { shutdownNow() }).get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalStateException("control shutdown", e)
        } finally {
            queue.shutdownNow()
            try {
                check(queue.awaitTermination(5, TimeUnit.SECONDS)) { "control thread still running" }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            repository.close()
        }
    }
}
