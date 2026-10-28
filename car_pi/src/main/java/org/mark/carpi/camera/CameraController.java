package org.mark.carpi.camera;

import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.Arrays;

/** Fixture-only backend. Lifecycle is serialized by the command dispatcher. */
public final class CameraController implements AutoCloseable {
    private final String mode, bind;
    private final int port;
    private DatagramSocket socket;
    private Thread worker;
    public CameraController(String mode,String bind,int port) { this.mode=mode;this.bind=bind;this.port=port; }
    public static CameraController disabled() { return new CameraController("disabled","127.0.0.1",0); }
    public String info() {
        // Android Size is serialized by old Gson with its mWidth/mHeight fields.
        return mode.equals("fixture") ? "{\"sizes\":[{\"mWidth\":320,\"mHeight\":240}],\"hardwareLevel\":null}"
            : "{\"sizes\":[],\"hardwareLevel\":null}";
    }
    public void command(JsonObject o) throws IOException {
        int action=o.has("action")?o.get("action").getAsInt():0;
        if(action==0) { close(); return; }
        if(!mode.equals("fixture")) throw new IOException("Camera backend not migrated; video unavailable");
        if(!o.has("width") || !o.has("height") || o.get("width").getAsInt()!=320 || o.get("height").getAsInt()!=240)
            throw new IOException("Fixture supports only 320x240");
        if(!o.has("transferVideo") || !o.get("transferVideo").getAsBoolean()) { close(); return; }
        if(socket!=null) return;
        byte[] packet=packet(new FixtureFrameSource().frame());
        DatagramSocket created=new DatagramSocket(null);
        try { created.bind(new InetSocketAddress(bind,port)); created.setSoTimeout(200); }
        catch(IOException e) { created.close(); throw e; }
        socket=created;
        worker=new Thread(()->send(created,packet),"fixture-video"); worker.start();
    }
    public static byte[] packet(byte[] jpeg) throws IOException {
        if(jpeg.length>65506) throw new IOException("JPEG exceeds single UDP payload limit");
        byte[] packet=new byte[jpeg.length+1];packet[0]=5;System.arraycopy(jpeg,0,packet,1,jpeg.length);return packet;
    }
    private void send(DatagramSocket socket,byte[] frame) {
        SocketAddress peer=null; long next=0;
        byte[] greeting={5,'h','e','l','l','o'};
        try {
            while(!socket.isClosed()) {
                DatagramPacket request=new DatagramPacket(new byte[256],256);
                try {
                    socket.receive(request);
                    if(Arrays.equals(Arrays.copyOf(request.getData(),request.getLength()),greeting)) peer=request.getSocketAddress();
                } catch(SocketTimeoutException ignored) { }
                long now=System.nanoTime();
                if(peer!=null && now>=next) { socket.send(new DatagramPacket(frame,frame.length,peer));next=now+200_000_000L; }
            }
        } catch(IOException e) { if(!socket.isClosed()) System.err.println("fixture-video: "+e); }
    }
    @Override public void close() {
        if(socket==null) return;
        socket.close();
        try { worker.join(3000); if(worker.isAlive()) throw new IllegalStateException("video thread still running"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        finally { socket=null;worker=null; }
    }
}
