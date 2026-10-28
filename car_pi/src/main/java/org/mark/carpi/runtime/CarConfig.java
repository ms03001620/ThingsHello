package org.mark.carpi.runtime;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public record CarConfig(String bind, int port, Path actions, String camera, int udpPort) {
    public CarConfig(String bind, int port, Path actions) { this(bind,port,actions,"disabled",0); }
    public static CarConfig load(Path path) throws IOException {
        Properties p = new Properties();
        if (path != null) try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        if (!p.getProperty("backend","recording").equals("recording"))
            throw new IllegalArgumentException("Only recording is verified; real IO unavailable");
        int port = Integer.parseInt(p.getProperty("tcp.port","8010"));
        if (port < 0 || port > 65535) throw new IllegalArgumentException("tcp.port");
        String camera = p.getProperty("camera","disabled");
        if (!java.util.Set.of("disabled","fixture").contains(camera)) throw new IllegalArgumentException("Camera backend unavailable: " + camera);
        int udpPort = Integer.parseInt(p.getProperty("udp.port","8000"));
        if (udpPort < 0 || udpPort > 65535) throw new IllegalArgumentException("udp.port");
        return new CarConfig(p.getProperty("bind","0.0.0.0"),port,Path.of(p.getProperty("actions.path","results/actions.jsonl")),camera,udpPort);
    }
}
