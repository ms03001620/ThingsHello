package org.mark.carpi.runtime;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public record CarConfig(String bind, int port, Path actions) {
    public static CarConfig load(Path path) throws IOException {
        Properties p = new Properties();
        if (path != null) try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        if (!p.getProperty("backend","recording").equals("recording"))
            throw new IllegalArgumentException("Only recording is verified; real IO unavailable");
        int port = Integer.parseInt(p.getProperty("tcp.port","8010"));
        if (port < 0 || port > 65535) throw new IllegalArgumentException("tcp.port");
        return new CarConfig(p.getProperty("bind","0.0.0.0"),port,Path.of(p.getProperty("actions.path","results/actions.jsonl")));
    }
}
