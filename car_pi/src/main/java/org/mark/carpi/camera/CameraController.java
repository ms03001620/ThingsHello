package org.mark.carpi.camera;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Capture and UDP receive run separately from the single control-command queue. */
public final class CameraController implements AutoCloseable {
    private final String mode,bind,executable;
    private final int port;
    private volatile Session session;
    private volatile String error="";
    private volatile boolean captured;
    private final AtomicLong sent=new AtomicLong(), dropped=new AtomicLong();
    public CameraController(String mode,String bind,int port) { this(mode,bind,port,"rpicam-vid"); }
    public CameraController(String mode,String bind,int port,String executable) { this.mode=mode;this.bind=bind;this.port=port;this.executable=executable; }
    public static CameraController disabled() { return new CameraController("disabled","127.0.0.1",0); }
    public String info() {
        JsonObject info=new JsonObject();JsonArray sizes=new JsonArray();
        if(mode.equals("fixture") || captured) {
            JsonObject size=new JsonObject();size.addProperty("mWidth",320);size.addProperty("mHeight",240);sizes.add(size);
        }
        info.add("sizes",sizes);info.add("hardwareLevel",JsonNull.INSTANCE);
        info.addProperty("backend",mode);info.addProperty("error",error);
        info.addProperty("sentFrames",sent.get());info.addProperty("droppedFrames",dropped.get());
        return new GsonBuilder().serializeNulls().create().toJson(info);
    }
    public void command(JsonObject o) throws IOException {
        int action=o.has("action")?o.get("action").getAsInt():0;
        if(action==0) { close();return; }
        if(!Set.of("fixture","rpicam").contains(mode)) throw new IOException("Camera disabled; video unavailable");
        if(!o.has("width") || !o.has("height") || o.get("width").getAsInt()!=320 || o.get("height").getAsInt()!=240)
            throw new IOException("Capture adapter accepts only 320x240");
        if(!o.has("transferVideo") || !o.get("transferVideo").getAsBoolean()) { close();return; }
        if(session!=null && session.sender.isAlive()) return;
        close();error="";
        DatagramSocket socket=new DatagramSocket(null);FrameSource source=null;
        try {
            socket.bind(new InetSocketAddress(bind,port));
            source=mode.equals("fixture")?new FixtureFrameSource():new RpicamFrameSource(executable);
            Session next=new Session(socket,source);session=next;next.receiver.start();next.sender.start();
        } catch(IOException | RuntimeException e) { socket.close();if(source!=null) source.close();throw e; }
    }
    public static byte[] packet(byte[] jpeg) throws IOException {
        if(jpeg.length>65506) throw new IOException("JPEG exceeds single UDP payload limit");
        byte[] data=new byte[jpeg.length+1];data[0]=5;System.arraycopy(jpeg,0,data,1,jpeg.length);return data;
    }
    private final class Session {
        final DatagramSocket socket;final FrameSource source;final Thread receiver,sender;
        volatile SocketAddress peer;
        volatile boolean stopping;
        Session(DatagramSocket socket,FrameSource source) {
            this.socket=socket;this.source=source;
            receiver=new Thread(()->{
                try {
                    while(!socket.isClosed()) {
                        DatagramPacket request=new DatagramPacket(new byte[256],256);socket.receive(request);
                        if(Arrays.equals(Arrays.copyOf(request.getData(),request.getLength()),new byte[]{5,'h','e','l','l','o'})) peer=request.getSocketAddress();
                    }
                } catch(IOException e) { if(!stopping && !socket.isClosed()) error=e.toString(); }
            },"video-handshake");
            sender=new Thread(()->{
                try {
                    while(!stopping) {
                        byte[] jpeg=source.frame();captured=true;
                        if(jpeg.length>65506) dropped.incrementAndGet();
                        else if(peer!=null) { byte[] data=packet(jpeg);socket.send(new DatagramPacket(data,data.length,peer));sent.incrementAndGet(); }
                        if(mode.equals("fixture")) Thread.sleep(200);
                    }
                } catch(IOException | InterruptedException e) {
                    if(!stopping) { error=e.toString();System.err.println("camera: "+error); }
                } finally {
                    stopping=true;socket.close();
                    try { source.close(); } catch(IOException e) { error=e.toString(); }
                }
            },"video-capture");
        }
    }
    @Override public void close() {
        Session old=session;if(old==null)return;
        old.stopping=true;old.socket.close();old.sender.interrupt();
        try {
            // Sender owns source closure, including subprocess termination.
            old.sender.join(3000);old.receiver.join(1000);
            if(old.sender.isAlive() || old.receiver.isAlive()) throw new IllegalStateException("Video workers did not stop");
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
        session=null;
    }
}
