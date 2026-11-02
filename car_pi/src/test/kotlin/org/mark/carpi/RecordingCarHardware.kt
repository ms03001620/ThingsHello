package org.mark.carpi

import org.mark.carpi.domain.CarHardware
import org.mark.carpi.hardware.logging.ActionLogRecorder
import org.mark.carpi.hardware.logging.LoggingCarHardware
import org.mark.carpi.hardware.simulated.SimulatedHardware

/** 测试用：no-op 硬件 + 内存记录器，记录与 actions.jsonl 相同的 (id, action, value)。 */
fun recordingHardware(
    delegate: CarHardware = SimulatedHardware(),
    onClose: () -> Unit = {},
    sink: (Long, String, Any?) -> Unit,
) = LoggingCarHardware(delegate, object : ActionLogRecorder {
    override fun record(command: Long, action: String, value: Any?) = sink(command, action, value)
    override fun close() = onClose()
})
