package org.mark.carpi.hardware.linux

import java.util.Properties

/**
 * GPIO 硬件配置（service.properties 中 hardware.* / wheel.* / light.* / buzzer.* / servo.* / pwm.*）。
 */
data class HardwareConfig(
    val mode: HardwareMode,
    val left: Motor?,
    val right: Motor?,
    val lights: List<Int>,
    val lightHigh: Boolean?,
    val buzzer: Int?,
    val buzzerHigh: Boolean?,
    val servo: Int?,
    val frequency: Int,
    val maxError: Int,
    val startupBeepMs: Int,
) {
    data class Motor(
        val in1: Int,
        val in2: Int,
        val pwm: Int,
        val forwardHigh: Boolean,
    )

    val real: Boolean get() = mode == HardwareMode.REAL

    companion object {
        const val DEFAULT_STARTUP_BEEP_MS = 1000

        fun simulated(startupBeepMs: Int = DEFAULT_STARTUP_BEEP_MS) =
            HardwareConfig(HardwareMode.SIMULATED, null, null, listOf(), null, null, null, null, 300, 0, startupBeepMs)

        fun load(p: Properties): HardwareConfig {
            val mode = mode(p)
            // buzzer.startupMs：启动成功后蜂鸣时长，0 为关闭
            val beep = number(p, "buzzer.startupMs", DEFAULT_STARTUP_BEEP_MS, 0, 10000)
            if (mode == HardwareMode.SIMULATED) return simulated(beep)
            require(flag(p, "hardware.confirmed", false)) { "Real hardware requires hardware.confirmed=true after wiring/polarity review" }
            var left: Motor? = null
            var right: Motor? = null
            var lights = listOf<Int>()
            var lightHigh: Boolean? = null
            var buzzer: Int? = null
            var buzzerHigh: Boolean? = null
            var servo: Int? = null
            if (flag(p, "wheel.enabled", false)) {
                left = motor(p, "left", 21, 20, 16)
                right = motor(p, "right", 26, 19, 13)
            }
            if (flag(p, "light.enabled", false)) {
                lights = listOf(pin(p, "light.red", 22), pin(p, "light.green", 27), pin(p, "light.blue", 24))
                lightHigh = requiredFlag(p, "light.activeHigh")
            }
            if (flag(p, "buzzer.enabled", false)) {
                buzzer = pin(p, "buzzer.pin", 8)
                buzzerHigh = requiredFlag(p, "buzzer.activeHigh")
            }
            if (flag(p, "servo.enabled", false)) {
                require(p.containsKey("servo.pin")) { "Explicit BCM servo.pin required; PWM0 is not a BCM pin" }
                servo = pin(p, "servo.pin", -1)
            }
            require(servo == null || servo == 18) { "lgpio hardware PWM servo requires BCM18 / PWM0" }
            val all = buildList {
                addAll(lights)
                if (left != null && right != null) addAll(listOf(left.in1, left.in2, left.pwm, right.in1, right.in2, right.pwm))
                buzzer?.let { add(it) }
                servo?.let { add(it) }
            }
            require(all.isNotEmpty()) { "No hardware device enabled" }
            require(all.toSet().size == all.size) { "Overlapping BCM pins" }
            val hz = number(p, "pwm.frequencyHz", 300, 1, 8000)
            val error = number(p, "pwm.maxErrorHz", 0, 0, 8000)
            return HardwareConfig(mode, left, right, lights, lightHigh, buzzer, buzzerHigh, servo, hz, error, beep)
        }

        private fun mode(p: Properties): HardwareMode {
            // 旧键 backend= 已移除：显式拒绝，避免旧配置被静默当成 simulated
            require(!p.containsKey("backend")) { "Config key 'backend' was removed; use hardware.mode=real|simulated" }
            return p.getProperty("hardware.mode")?.let(HardwareMode::of) ?: HardwareMode.SIMULATED
        }

        private fun motor(p: Properties, name: String, a: Int, b: Int, pwm: Int) =
            Motor(pin(p, "$name.in1", a), pin(p, "$name.in2", b), pin(p, "$name.pwm", pwm), requiredFlag(p, "$name.forwardHigh"))

        private fun pin(p: Properties, key: String, fallback: Int) = number(p, key, fallback, 0, 31)

        private fun number(p: Properties, key: String, fallback: Int, min: Int, max: Int): Int {
            val n = p.getProperty(key, fallback.toString()).toInt()
            require(n in min..max) { key }
            return n
        }

        private fun requiredFlag(p: Properties, key: String): Boolean {
            require(p.containsKey(key)) { "Explicit polarity required: $key" }
            return flag(p, key, false)
        }

        private fun flag(p: Properties, key: String, fallback: Boolean): Boolean {
            val v = p.getProperty(key, fallback.toString())
            require(v == "true" || v == "false") { key }
            return v.toBoolean()
        }
    }
}
