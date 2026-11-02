package org.mark.carpi.hardware.logging;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

/** Writes the action log for simulated or real hardware mode. */
public final class JsonlActionLogRecorder implements ActionLogRecorder {
    private final BufferedWriter writer;
    private final String mode;
    public JsonlActionLogRecorder(Path path, String mode) throws IOException {
        this.mode = mode;
        Files.createDirectories(path.toAbsolutePath().getParent());
        writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    @Override public synchronized void record(long command, String action, Object value) {
        try {
            writer.write(new Gson().toJson(Map.of("time", Instant.now().toString(), "command", command,
                "action", action, "value", value, "mode", mode)));
            writer.newLine(); writer.flush();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public synchronized void close() {
        try { writer.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
