package org.mark.carpi.runtime;

import org.mark.carpi.control.CommandDispatcher;
import org.mark.carpi.hardware.recording.RecordingHardware;
import org.mark.carpi.protocol.FrameCodec;
import java.io.*;
import org.mark.carpi.hardware.Hardware;
import org.mark.carpi.hardware.linux.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CarRuntime implements AutoCloseable {
    private final ServerSocket server = new ServerSocket();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final CommandDispatcher dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean();
    public CarRuntime(CarConfig config) throws IOException {
        CommandDispatcher created = null;
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(config.bind(),config.port()));
            created = new CommandDispatcher(hardware(config), new org.mark.carpi.camera.CameraController(config.camera(),config.bind(),config.udpPort()));
        } catch (IOException | RuntimeException e) { server.close(); if (created != null) created.close(); throw e; }
        dispatcher = created;
    }
    private static Hardware hardware(CarConfig config) throws IOException {
        Hardware audit=new RecordingHardware(config.actions(),config.hardware().backend());
        if(config.hardware().backend().equals("recording"))return audit;
        try { return new PigpioHardware(new PigpioClient(config.hardware().host(),config.hardware().port()),config.hardware(),audit); }
        catch(IOException | RuntimeException e) { audit.close();throw e; }
    }
    public int port() { return server.getLocalPort(); }
    public void start() { workers.submit(this::accept); }
    private void accept() {
        while (!closed.get()) {
            try {
                Socket socket = server.accept();
                synchronized (clients) {
                    if (closed.get()) { socket.close(); return; }
                    // Old phone is the single controller; bound resources for idle clients.
                    if (!clients.isEmpty()) { socket.close(); continue; }
                    clients.add(socket); workers.submit(() -> read(socket));
                }
            } catch (IOException e) { if (!closed.get()) System.err.println("accept: " + e); }
        }
    }
    private void read(Socket socket) {
        try (socket) {
            socket.setTcpNoDelay(true);
            while (!closed.get()) {
                var frame = FrameCodec.read(socket.getInputStream());
                if (frame == null) break;
                // Await dispatch to preserve order and bound queued commands.
                dispatcher.submit(frame, reply -> {
                    try { socket.getOutputStream().write(FrameCodec.encode(reply.type(),reply.body())); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                }).get();
            }
        } catch (Exception e) { if (!closed.get()) System.err.println("client: " + e); }
        finally { clients.remove(socket); }
    }
    public void close() {
        if (!closed.compareAndSet(false,true)) return;
        try { server.close(); } catch (IOException e) { System.err.println(e); }
        synchronized (clients) { for (Socket s : clients) try { s.close(); } catch (IOException e) { System.err.println(e); } }
        workers.shutdown();
        try { if (!workers.awaitTermination(5,TimeUnit.SECONDS)) workers.shutdownNow(); }
        catch (InterruptedException e) { workers.shutdownNow(); Thread.currentThread().interrupt(); }
        dispatcher.close();
    }
}
