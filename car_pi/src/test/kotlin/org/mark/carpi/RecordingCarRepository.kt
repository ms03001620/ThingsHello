package org.mark.carpi

import org.mark.carpi.repository.CarRepository
import org.mark.carpi.repository.ActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository

/** 测试用：no-op 硬件 + 内存记录器，记录与 actions.jsonl 相同的 (id, action, value)。 */
fun recordingRepository(
    delegate: CarRepository = SimulatedCarRepository(),
    onClose: () -> Unit = {},
    sink: (Long, String, Any?) -> Unit,
) = LoggingCarRepository(delegate, object : ActionLogRecorder {
    override fun record(command: Long, action: String, value: Any?) = sink(command, action, value)
    override fun close() = onClose()
})
