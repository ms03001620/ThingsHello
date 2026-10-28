package org.mark.carpi.hardware.recording;

import com.google.gson.Gson;
import org.mark.carpi.hardware.Hardware;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

/** Records intended actions only; never claims physical output. */
public final class RecordingHardware implements Hardware {
    private final BufferedWriter writer;
    public RecordingHardware(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    public synchronized void action(long command, String action, Object value) {
        try {
            writer.write(new Gson().toJson(Map.of("time", Instant.now().toString(), "command", command,
                "action", action, "value", value, "backend", "recording")));
            writer.newLine(); writer.flush();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public synchronized void close() {
        try { writer.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
