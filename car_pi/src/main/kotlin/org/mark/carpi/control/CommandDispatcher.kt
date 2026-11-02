package org.mark.carpi.control

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.mark.carpi.hardware.Hardware
import org.mark.carpi.protocol.FrameCodec.Frame
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Behavioral mapping: WheelAction, CameraServo, ExclusiveBind, ForwardLightAction, BuzzerAction.
 * 所有状态（owner/sequence/定时释放）只在单线程 queue 上读写，指令与定时释放严格串行。
 */
class CommandDispatcher(private val hardware: Hardware) : AutoCloseable {
    private val queue: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var servoRelease: ScheduledFuture<*>? = null
    private var buzzerStop: ScheduledFuture<*>? = null
    private var owner = NONE
    private var sequence = 0L

    fun submit(frame: Frame, response: (Frame) -> Unit): Future<*> = queue.submit(Runnable {
        val id = ++sequence
        hardware.action(id, "command", mapOf("type" to frame.type, "body" to frame.body))
        try {
            dispatch(id, frame, response)
        } catch (e: RuntimeException) {
            hardware.action(id, "rejected", e.toString())
        }
    })

    private fun activate(id: Long, next: String) {
        if (next == owner) return
        servoRelease?.cancel(false)
        if (owner != NONE) hardware.action(id, "$owner.release", true)
        owner = NONE
        hardware.action(id, "$next.bind", true)
        owner = next
    }

    @Suppress("UNUSED_PARAMETER") // 协议目前没有回包；保留回调以便将来回复手机
    private fun dispatch(id: Long, frame: Frame, response: (Frame) -> Unit) {
        val o = JsonParser.parseString(frame.body).asJsonObject
        when (frame.type) {
            0 -> {
                val left = integer(o, "left", 0, -100, 100)
                val right = integer(o, "right", 0, -100, 100)
                activate(id, "wheel")
                hardware.action(id, "wheel.speed", mapOf("left" to left, "right" to right))
            }
            1 -> {
                // 非短路 or：三个分量都要校验
                val on = (integer(o, "red", 0, 0, 255) > 0) or (integer(o, "green", 0, 0, 255) > 0) or
                    (integer(o, "blue", 0, 0, 255) > 0)
                val level = if (on) 100 else 0
                hardware.action(id, "light.rgb", listOf(level, level, level))
            }
            2 -> if (o.has("di") && o.get("di").asBoolean) beepNow(id, 500)
            4 -> {
                val progress = integer(o, "progress", 0, 0, 100)
                activate(id, "servo")
                servoRelease?.cancel(false)
                hardware.action(id, "servo.pulseMs", 0.8 + 0.007 * progress)
                servoRelease = queue.schedule({
                    if (owner == "servo") {
                        hardware.action(id, "servo.release", true)
                        owner = NONE
                    }
                }, 500, TimeUnit.MILLISECONDS)
            }
            6 -> {
                val speed = integer(o, "speed", 40, 0, 100)
                val rotate = if (o.has("rotate")) o.get("rotate").asString else "LEFT"
                require(rotate in setOf("LEFT", "RIGHT")) { "rotate" }
                activate(id, "wheel")
                val left = rotate == "LEFT"
                hardware.action(id, "wheel.speed", mapOf("left" to if (left) -speed else speed, "right" to if (left) speed else -speed))
            }
            else -> hardware.action(id, "unsupported.type", frame.type)
        }
    }

    private fun beepNow(id: Long, ms: Int) {
        buzzerStop?.cancel(false)
        hardware.action(id, "buzzer.enabled", true)
        buzzerStop = queue.schedule({ hardware.action(id, "buzzer.enabled", false) }, ms.toLong(), TimeUnit.MILLISECONDS)
    }

    /** 蜂鸣 ms 毫秒（如启动提示）；与手机指令同队列串行，close() 会取消并经 all.stop 关闭蜂鸣器。 */
    fun beep(ms: Int): Future<*> = queue.submit(Runnable {
        try {
            beepNow(0, ms)
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    })

    /** Release motion on control disconnect; do not leave a physical motor running without its client. */
    fun releaseControl(): Future<*> = queue.submit(Runnable {
        servoRelease?.cancel(false)
        try {
            if (owner != NONE) hardware.action(0, "$owner.release", true)
        } finally {
            owner = NONE
        }
    })

    override fun close() {
        try {
            queue.submit(Runnable {
                servoRelease?.cancel(false)
                buzzerStop?.cancel(false)
                try {
                    if (owner != NONE) hardware.action(0, "$owner.release", true)
                } finally {
                    hardware.action(0, "all.stop", true)
                }
            }).get(5, TimeUnit.SECONDS)
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

    private companion object {
        const val NONE = "none"

        fun integer(o: JsonObject, key: String, fallback: Int, min: Int, max: Int): Int {
            if (!o.has(key)) return fallback
            val v = o.get(key)
            require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber) { key }
            val n = v.asBigDecimal.intValueExact()
            require(n in min..max) { "$key out of range" }
            return n
        }
    }
}
