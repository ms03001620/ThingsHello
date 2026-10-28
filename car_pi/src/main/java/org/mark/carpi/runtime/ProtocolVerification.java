package org.mark.carpi.runtime;

import com.google.gson.*;
import org.mark.carpi.protocol.FrameCodec;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Runs the real TCP server with a recording driver; never outputs hardware signals. */
public final class ProtocolVerification {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static Map<String,Object> run() throws Exception {
        Path log = Files.createTempFile("car-pi-protocol-", ".jsonl");
        try {
            int port;
            try (var runtime = new CarRuntime(new CarConfig("127.0.0.1",0,log))) {
                runtime.start(); port=runtime.port();
                try (Socket socket = new Socket("127.0.0.1",port)) {
                    socket.setSoTimeout(3000);
                    OutputStream out=socket.getOutputStream();
                    byte[] split = FrameCodec.encode(0,"{\"left\":35,\"right\":-20}");
                    for (byte b:split) { out.write(b); out.flush(); }
                    ByteArrayOutputStream batch=new ByteArrayOutputStream();
                    batch.write(FrameCodec.encode(1,"{\"red\":1,\"green\":0,\"blue\":0}"));
                    batch.write(FrameCodec.encode(2,"{\"di\":true}"));
                    batch.write(FrameCodec.encode(4,"{\"progress\":100}"));
                    batch.write(FrameCodec.encode(3,"{\"action\":1,\"width\":320,\"height\":240,\"transferVideo\":true,\"tfCmd\":{}}"));
                    out.write(batch.toByteArray()); out.flush();
                    var info=FrameCodec.read(socket.getInputStream());
                    check(info.type()==5 && JsonParser.parseString(info.body()).getAsJsonObject().getAsJsonArray("sizes").isEmpty(),"camera info shape");
                    check(FrameCodec.read(socket.getInputStream()).type()==-1,"signed text type");
                    check(FrameCodec.read(socket.getInputStream()).body().contains("TensorFlow unsupported"),"unsupported inference reported");
                    awaitActions(log, "servo.release", "buzzer.enabled");
                    out.write(FrameCodec.encode(6,"{\"rotate\":\"LEFT\",\"speed\":40}"));
                    out.write(FrameCodec.encode(6,"{\"rotate\":\"RIGHT\",\"speed\":25}"));
                    out.write(FrameCodec.encode(0,"{\"left\":0,\"right\":0}"));
                    out.write(FrameCodec.encode(4,"{\"progress\":101}"));
                    out.write(FrameCodec.encode(1,"not json"));
                    out.write(FrameCodec.encode(5,"{}"));
                    check(FrameCodec.read(socket.getInputStream()).type()==5,"connection survives rejected commands");
                }
            }
            List<JsonObject> events=events(log);
            expect(events,1,"wheel.speed","{\"left\":35,\"right\":-20}");
            expect(events,2,"light.rgb","[100,100,100]");
            expect(events,3,"buzzer.enabled","true"); expect(events,3,"buzzer.enabled","false");
            expect(events,4,"wheel.release","true"); expect(events,4,"servo.pulseMs","1.5"); expect(events,4,"servo.release","true");
            expect(events,6,"wheel.speed","{\"left\":-40,\"right\":40}");
            expect(events,7,"wheel.speed","{\"left\":25,\"right\":-25}");
            expect(events,8,"wheel.speed","{\"left\":0,\"right\":0}");
            check(events.stream().filter(e->e.get("action").getAsString().equals("rejected")).count()==2,"invalid commands rejected");
            try (ServerSocket rebound = new ServerSocket()) { rebound.setReuseAddress(true); rebound.bind(new InetSocketAddress("127.0.0.1",port)); }
            return Map.of("status","PASS","verification","已验证","scope","TCP loopback + recording, no physical output or old phone acceptance",
                "checks",List.of("fragmented header/body","coalesced frames","types 0..6","signed 0xFF response","recorded action values","servo/wheel exclusion","500ms releases","invalid JSON/range rejection","port rebind after shutdown"),"events",events);
        } finally { Files.deleteIfExists(log); }
    }
    private static void awaitActions(Path log,String a,String b) throws Exception {
        long until=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<until) {
            var all=events(log);
            if(all.stream().anyMatch(e->e.get("action").getAsString().equals(a)) && all.stream().anyMatch(e->e.get("action").getAsString().equals(b) && e.get("value").toString().equals("false"))) return;
            Thread.sleep(20);
        }
        throw new AssertionError("timed releases missing");
    }
    private static List<JsonObject> events(Path log) throws IOException {
        return Files.readAllLines(log).stream().filter(s->!s.isBlank()).map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
    }
    private static void expect(List<JsonObject> events,long command,String action,String value) {
        check(events.stream().anyMatch(e->e.get("command").getAsLong()==command && e.get("action").getAsString().equals(action) && e.get("value").equals(JsonParser.parseString(value))),"missing " + command+" "+action+"="+value);
    }
    private ProtocolVerification() {}
}
