package org.mark.carpi.hardware.simulated;


import org.mark.carpi.hardware.Hardware;
import org.mark.carpi.hardware.logging.ActionLogRecorder;
import org.mark.carpi.hardware.logging.JsonlActionLogRecorder;
import java.io.IOException;
import java.nio.file.Path;

/** Records intended actions only; never claims physical output. */
public final class SimulatedHardware implements Hardware {
    private final ActionLogRecorder recorder;
    public SimulatedHardware(Path path) throws IOException {
        recorder = new JsonlActionLogRecorder(path, "simulated");
    }
    @Override public void action(long command, String action, Object value) {
        recorder.record(command, action, value);
    }
    @Override public void close() { recorder.close(); }
}
