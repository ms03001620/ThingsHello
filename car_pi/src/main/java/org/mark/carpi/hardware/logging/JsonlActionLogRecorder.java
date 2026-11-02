package org.mark.carpi.hardware.logging;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

/** Writes the action log for either a simulated or physical hardware backend. */
public final class JsonlActionLogRecorder implements ActionLogRecorder {
    private final BufferedWriter writer;
    private final String backend;
    public JsonlActionLogRecorder(Path path, String backend) throws IOException {
        this.backend = backend;
        Files.createDirectories(path.toAbsolutePath().getParent());
        writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    @Override public synchronized void record(long command, String action, Object value) {
        try {
            writer.write(new Gson().toJson(Map.of("time", Instant.now().toString(), "command", command,
                "action", action, "value", value, "backend", backend)));
            writer.newLine(); writer.flush();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public synchronized void close() {
        try { writer.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
