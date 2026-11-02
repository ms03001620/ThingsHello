package org.mark.carpi.repository.linux

import org.mark.carpi.repository.ActionLog
import org.mark.carpi.repository.CarRepository
import java.io.IOException
import java.io.UncheckedIOException
import kotlin.math.abs
import kotlin.math.sign

/** Shared Linux action driver; transport owns native GPIO/PWM timing and resource release. */
class LinuxCarRepository(
    private val io: GpioBackend,
    private val config: HardwareConfig,
    /** 内部事件（pwm.configuredHz）写入同一动作日志；recorder 的关闭由 LoggingCarRepository 负责。 */
    private val events: ActionLog,
) : CarRepository {
    private var faulted = false
    private var closed = false
    private val speeds = HashMap<HardwareConfig.Motor, Int>()
    /** left, right；wheel.enabled=false 时为空。 */
    private val motors = listOfNotNull(config.left, config.right)

    override fun wheelBind(id: Long) = guarded {
        requireDevice(motors.isNotEmpty(), "wheel")
        motors.forEach { bind(it, id) }
    }

    override fun wheelSpeed(id: Long, left: Int, right: Int) = guarded {
        requireDevice(motors.isNotEmpty(), "wheel")
        drive(motors[0], left)
        drive(motors[1], right)
    }

    override fun wheelRelease(id: Long) = guarded { motors.forEach { releaseMotor(it) } }

    override fun servoBind(id: Long) = guarded { requireDevice(config.servo != null, "servo") }

    override fun servoPulseMs(id: Long, pulseMs: Double) = guarded {
        val servo = config.servo
        requireDevice(servo != null, "servo")
        io.servo(servo!!, Math.round(pulseMs * 1000).toInt())
    }

    override fun servoRelease(id: Long) = guarded {
        config.servo?.let {
            io.servo(it, 0)
            io.mode(it, 0)
        }
    }

    /** 数字输出：三路 (red, green, blue) 同时写 on==activeHigh，没有 PWM 调光。 */
    override fun light(id: Long, on: Boolean) = guarded {
        requireDevice(config.lights.isNotEmpty(), "light")
        for (i in 0 until 3) io.write(config.lights[i], on == config.lightHigh)
    }

    override fun buzzer(id: Long, enabled: Boolean) = guarded {
        val buzzer = config.buzzer
        requireDevice(buzzer != null, "buzzer")
        io.write(buzzer!!, enabled == config.buzzerHigh)
    }

    override fun allStop(id: Long) = guarded { stopAll() }

    /** 故障/关闭后拒绝一切调用；IOException 时进入 faulted：尽力停全部并关闭 io。 */
    @Synchronized
    private fun guarded(op: () -> Unit) {
        check(!faulted && !closed) { "Hardware faulted/closed; restart required" }
        try {
            op()
        } catch (e: IOException) {
            faulted = true
            try { stopAll() } catch (stop: IOException) { e.addSuppressed(stop) }
            try { io.close() } catch (close: IOException) { e.addSuppressed(close) }
            throw UncheckedIOException(e)
        }
    }

    private fun requireDevice(enabled: Boolean, device: String) = check(enabled) { "$device disabled in hardware config" }

    private fun bind(m: HardwareConfig.Motor, command: Long) {
        io.pwm(m.pwm, 0)
        io.write(m.in1, false)
        io.write(m.in2, false)
        io.range(m.pwm, 100)
        speeds[m] = 0
        val actual = io.frequency(m.pwm, config.frequency)
        events.event(command, "pwm.configuredHz", mapOf("pin" to m.pwm, "requested" to config.frequency, "actual" to actual))
        if (abs(actual - config.frequency) > config.maxError)
            throw IOException("PWM frequency mismatch: requested ${config.frequency}, actual $actual")
    }

    private fun drive(m: HardwareConfig.Motor, speed: Int) {
        val previous = speeds[m] ?: 0
        if (speed == previous) return
        if (speed.sign != previous.sign) {
            io.pwm(m.pwm, 0)
            io.write(m.in1, speed != 0 && ((speed > 0) == m.forwardHigh))
            io.write(m.in2, speed != 0 && ((speed > 0) != m.forwardHigh))
        }
        io.pwm(m.pwm, abs(speed))
        speeds[m] = speed
    }

    /** 逐个执行全部释放动作，任一失败也继续；首个 IOException 抛出，其余作为 suppressed。 */
    private fun attemptAll(operations: List<() -> Unit>) {
        var failure: IOException? = null
        for (op in operations) {
            try {
                op()
            } catch (e: IOException) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }

    private fun releaseMotor(m: HardwareConfig.Motor) {
        speeds.remove(m)
        attemptAll(listOf(
            { io.pwm(m.pwm, 0) }, { io.write(m.in1, false) }, { io.write(m.in2, false) },
            { io.mode(m.pwm, 0) }, { io.mode(m.in1, 0) }, { io.mode(m.in2, 0) },
        ))
    }

    private fun stopAll() {
        val operations = buildList<() -> Unit> {
            for (m in motors) add { releaseMotor(m) }
            config.servo?.let { servo ->
                add { io.servo(servo, 0) }
                add { io.mode(servo, 0) }
            }
            for (p in config.lights) {
                add { io.write(p, config.lightHigh != true) }
                add { io.mode(p, 0) }
            }
            config.buzzer?.let { buzzer ->
                add { io.write(buzzer, config.buzzerHigh != true) }
                add { io.mode(buzzer, 0) }
            }
        }
        attemptAll(operations)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            if (!faulted) stopAll()
        } catch (e: IOException) {
            System.err.println("Hardware stop failed: $e")
        } finally {
            try { io.close() } catch (e: IOException) { System.err.println(e) }
        }
    }
}
