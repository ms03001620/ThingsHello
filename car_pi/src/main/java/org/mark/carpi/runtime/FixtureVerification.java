package org.mark.carpi.runtime;

import org.mark.carpi.camera.*;
import org.mark.carpi.protocol.FrameCodec;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;

public final class FixtureVerification {
    public static Map<String,Object> run() throws Exception {
        Path log=Files.createTempFile("car-pi-video-",".jsonl");
        int udpPort;
        try(DatagramSocket probe=new DatagramSocket(0)) { udpPort=probe.getLocalPort(); }
        byte[] expected=CameraController.packet(new FixtureFrameSource().frame());
        try(var runtime=new CarRuntime(new CarConfig("127.0.0.1",0,log,"fixture",udpPort))) {
            runtime.start();
            try(Socket tcp=new Socket("127.0.0.1",runtime.port())) {
                tcp.setSoTimeout(3000);
                for(int i=0;i<10;i++) {
                    tcp.getOutputStream().write(FrameCodec.encode(3,"{\"action\":1,\"width\":320,\"height\":240,\"transferVideo\":true}"));
                    require(FrameCodec.read(tcp.getInputStream()).type()==5,"camera info");
                    // A type-5 query is an ordering barrier: open has completed before hello.
                    tcp.getOutputStream().write(FrameCodec.encode(5,"{}")); FrameCodec.read(tcp.getInputStream());
                    try(DatagramSocket client=new DatagramSocket(0)) {
                        client.setSoTimeout(3000);
                        byte[] hello={5,'h','e','l','l','o'};
                        client.send(new DatagramPacket(hello,hello.length,InetAddress.getLoopbackAddress(),udpPort));
                        DatagramPacket packet=new DatagramPacket(new byte[65507],65507); client.receive(packet);
                        require(packet.getPort()==udpPort,"server port");
                        require(Arrays.equals(expected,Arrays.copyOf(packet.getData(),packet.getLength())),"complete JPEG + 0x05 at actual client source port");
                        var image=ImageIO.read(new ByteArrayInputStream(packet.getData(),1,packet.getLength()-1));
                        require(image!=null && image.getWidth()==320 && image.getHeight()==240,"JPEG dimensions");
                    }
                    tcp.getOutputStream().write(FrameCodec.encode(3,"{\"action\":0}")); FrameCodec.read(tcp.getInputStream());
                    tcp.getOutputStream().write(FrameCodec.encode(5,"{}")); FrameCodec.read(tcp.getInputStream());
                    try(DatagramSocket rebound=new DatagramSocket(udpPort)) { require(rebound.isBound(),"UDP released"); }
                }
            }
        } finally { Files.deleteIfExists(log); }
        return Map.of("status","PASS","verification","已验证","scope","fixture loopback; phone display unverified","cycles",10,"jpegBytes",expected.length-1);
    }
    private static void require(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
}
