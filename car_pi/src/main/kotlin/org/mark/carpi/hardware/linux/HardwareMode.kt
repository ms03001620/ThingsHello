package org.mark.carpi.hardware.linux

/** 配置项 hardware.mode 的取值：real 经 lgpio 驱动真实 GPIO；simulated 只写动作日志（默认）。 */
enum class HardwareMode(val key: String) {
    REAL("real"),
    SIMULATED("simulated");

    override fun toString() = key

    companion object {
        fun of(key: String): HardwareMode =
            entries.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Unknown hardware.mode: $key (expected real|simulated)")
    }
}
