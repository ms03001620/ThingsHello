package org.mark.carpi.control

import org.mark.carpi.domain.ActionLog
import org.mark.carpi.domain.Cancellable
import org.mark.carpi.domain.CarHardware
import org.mark.carpi.domain.CarController
import org.mark.carpi.domain.Scheduler
import org.mark.carpi.hardware.logging.LoggingCarHardware
import org.mark.carpi.protocol.CommandParser
import org.mark.carpi.protocol.FrameCodec.Frame
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 运行时薄封装：单线程 queue 串行执行指令与定时任务，业务规则全部委托给 CarController。
 */
class CommandDispatcher(private val hardware: CarHardware, log: ActionLog) : AutoCloseable {
    /** 常用：同一个日志装饰器既是硬件端口也是事件日志。 */
    constructor(hardware: LoggingCarHardware) : this(hardware, hardware)

    private val queue: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val scheduler = Scheduler { delayMs, task ->
        val f = queue.schedule(Runnable { task() }, delayMs, TimeUnit.MILLISECONDS)
        Cancellable { f.cancel(false) }
    }
    private val controller = CarController(hardware, log, scheduler)

    @Suppress("UNUSED_PARAMETER") // 协议目前没有回包；保留回调以便将来回复手机
    fun submit(frame: Frame, response: (Frame) -> Unit): Future<*> = queue.submit(Runnable {
        controller.handle(frame.type, frame.body, CommandParser.parse(frame))
    })

    /** 蜂鸣 ms 毫秒（如启动提示）；与手机指令同队列串行，close() 会取消并经 all.stop 关闭蜂鸣器。 */
    fun beep(ms: Int): Future<*> = queue.submit(Runnable {
        try {
            controller.beep(0, ms)
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    })

    /** Release motion on control disconnect; do not leave a physical motor running without its client. */
    fun releaseControl(): Future<*> = queue.submit(Runnable { controller.releaseControl() })

    override fun close() {
        try {
            queue.submit(Runnable { controller.shutdown() }).get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalStateException("control shutdown", e)
        } finally {
            queue.shutdownNow()
            try {
                check(queue.awaitTermination(5, TimeUnit.SECONDS)) { "control thread still running" }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            hardware.close()
        }
    }
}
