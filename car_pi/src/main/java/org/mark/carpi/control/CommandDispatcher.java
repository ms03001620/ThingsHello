package org.mark.carpi.control;

import com.google.gson.*;
import org.mark.carpi.camera.CameraController;
import java.io.IOException;
import org.mark.carpi.hardware.Hardware;
import org.mark.carpi.protocol.FrameCodec.Frame;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Behavioral mapping: WheelAction, CameraServo, ExclusiveBind, ForwardLightAction, BuzzerAction. */
public final class CommandDispatcher implements AutoCloseable {
    private final Hardware hardware;
    private final CameraController camera;
    private final ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> servoRelease, buzzerStop;
    private String owner = "none";
    private long sequence;
    public CommandDispatcher(Hardware hardware) { this(hardware,CameraController.disabled()); }
    public CommandDispatcher(Hardware hardware, CameraController camera) { this.hardware=hardware; this.camera=camera; }
    public Future<?> submit(Frame frame, Consumer<Frame> response) {
        return queue.submit(() -> {
            long id = ++sequence;
            hardware.action(id, "command", Map.of("type", frame.type(), "body", frame.body()));
            try { dispatch(id, frame, response); }
            catch (RuntimeException e) { hardware.action(id, "rejected", e.toString()); }
        });
    }
    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return fallback;
        JsonElement v = o.get(key);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        int n = v.getAsBigDecimal().intValueExact();
        if (n < min || n > max) throw new IllegalArgumentException(key + " out of range");
        return n;
    }
    private void activate(long id, String next) {
        if (next.equals(owner)) return;
        if (servoRelease != null) servoRelease.cancel(false);
        if (!owner.equals("none")) hardware.action(id, owner + ".release", true);
        owner = "none";
        hardware.action(id, next + ".bind", true);
        owner = next;
    }
    private void dispatch(long id, Frame frame, Consumer<Frame> response) {
        JsonObject o = JsonParser.parseString(frame.body()).getAsJsonObject();
        switch (frame.type()) {
            case 0 -> {
                int left = integer(o, "left", 0, -100, 100), right = integer(o, "right", 0, -100, 100);
                activate(id, "wheel"); hardware.action(id, "wheel.speed", Map.of("left", left, "right", right));
            }
            case 1 -> {
                boolean on = integer(o,"red",0,0,255)>0 | integer(o,"green",0,0,255)>0 | integer(o,"blue",0,0,255)>0;
                hardware.action(id, "light.rgb", List.of(on ? 100 : 0, on ? 100 : 0, on ? 100 : 0));
            }
            case 2 -> {
                if (o.has("di") && o.get("di").getAsBoolean()) {
                    if (buzzerStop != null) buzzerStop.cancel(false);
                    hardware.action(id, "buzzer.enabled", true);
                    buzzerStop = queue.schedule(() -> hardware.action(id,"buzzer.enabled",false),500,TimeUnit.MILLISECONDS);
                }
            }
            case 3 -> {
                int action = integer(o,"action",0,0,1);
                hardware.action(id, "camera.request", o);
                response.accept(new Frame(5, camera.info()));
                try { camera.command(o); } catch(IOException e) { response.accept(new Frame(-1,e.getMessage())); }
                if (o.has("tfCmd") && !o.get("tfCmd").isJsonNull()) response.accept(new Frame(-1,"TensorFlow unsupported"));
            }
            case 4 -> {
                int progress = integer(o,"progress",0,0,100);
                activate(id,"servo");
                if (servoRelease != null) servoRelease.cancel(false);
                hardware.action(id,"servo.pulseMs",0.8 + 0.007 * progress);
                servoRelease = queue.schedule(() -> {
                    if (owner.equals("servo")) { hardware.action(id,"servo.release",true); owner="none"; }
                },500,TimeUnit.MILLISECONDS);
            }
            case 5 -> response.accept(new Frame(5,camera.info()));
            case 6 -> {
                int speed = integer(o,"speed",40,0,100);
                String rotate = o.has("rotate") ? o.get("rotate").getAsString() : "LEFT";
                if (!Set.of("LEFT","RIGHT").contains(rotate)) throw new IllegalArgumentException("rotate");
                activate(id,"wheel");
                hardware.action(id,"wheel.speed",Map.of("left",rotate.equals("LEFT") ? -speed : speed,
                    "right",rotate.equals("LEFT") ? speed : -speed));
            }
            default -> hardware.action(id,"unsupported.type",frame.type());
        }
    }
    /** Release motion on control disconnect; do not leave a physical motor running without its client. */
    public Future<?> releaseControl() {
        return queue.submit(() -> {
            if (servoRelease != null) servoRelease.cancel(false);
            try { if (!owner.equals("none")) hardware.action(0,owner+".release",true); }
            finally { owner="none"; }
        });
    }
    public void close() {
        try {
            queue.submit(() -> {
                if (servoRelease != null) servoRelease.cancel(false);
                if (buzzerStop != null) buzzerStop.cancel(false);
                try { if (!owner.equals("none")) hardware.action(0,owner+".release",true); }
                finally {
                    try { camera.close(); }
                    finally { hardware.action(0,"all.stop",true); }
                }
            }).get(5,TimeUnit.SECONDS);
        } catch (Exception e) { throw new IllegalStateException("control shutdown",e); }
        finally {
            queue.shutdownNow();
            try { if (!queue.awaitTermination(5,TimeUnit.SECONDS)) throw new IllegalStateException("control thread still running"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            hardware.close();
        }
    }
}
