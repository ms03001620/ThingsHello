package org.mark.carpi.hardware.simulated

import org.mark.carpi.hardware.Hardware
import org.mark.carpi.hardware.logging.ActionLogRecorder
import org.mark.carpi.hardware.logging.JsonlActionLogRecorder
import java.nio.file.Path

/** Records intended actions only; never claims physical output. */
class SimulatedHardware(path: Path) : Hardware {
    private val recorder: ActionLogRecorder = JsonlActionLogRecorder(path, "simulated")

    override fun action(command: Long, action: String, value: Any?) = recorder.record(command, action, value)

    override fun close() = recorder.close()
}
