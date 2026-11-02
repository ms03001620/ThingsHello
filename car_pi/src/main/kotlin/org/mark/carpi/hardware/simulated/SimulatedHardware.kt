package org.mark.carpi.hardware.simulated

import org.mark.carpi.domain.CarHardware
import org.mark.carpi.hardware.logging.JsonlActionLogRecorder
import org.mark.carpi.hardware.logging.LoggingCarHardware
import java.nio.file.Path

/** 模拟硬件：不输出任何物理信号；意图由 LoggingCarHardware 记录。 */
class SimulatedHardware : CarHardware {
    override fun wheelBind(id: Long) {}
    override fun wheelSpeed(id: Long, left: Int, right: Int) {}
    override fun wheelRelease(id: Long) {}
    override fun servoBind(id: Long) {}
    override fun servoPulseMs(id: Long, pulseMs: Double) {}
    override fun servoRelease(id: Long) {}
    override fun light(id: Long, on: Boolean) {}
    override fun buzzer(id: Long, enabled: Boolean) {}
    override fun allStop(id: Long) {}
    override fun close() {}

    companion object {
        /** 模拟模式：no-op 硬件 + mode=simulated 的 jsonl 日志。 */
        fun logged(path: Path) = LoggingCarHardware(SimulatedHardware(), JsonlActionLogRecorder(path, "simulated"))
    }
}
