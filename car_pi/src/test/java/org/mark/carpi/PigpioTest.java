package org.mark.carpi;

import org.junit.jupiter.api.Test;
import org.mark.carpi.hardware.*;
import org.mark.carpi.hardware.linux.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PigpioTest {
    @Test void wireFormatFragmentedResponseAndNegativeResult() throws Exception {
        try(var server=new ServerSocket(0);var workers=Executors.newSingleThreadExecutor()) {
            Future<?> peer=workers.submit(()->{
                try(var s=server.accept()) {
                    s.setSoTimeout(3000);
                    byte[] request=s.getInputStream().readNBytes(16);
                    assertArrayEquals(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(7).putInt(16).putInt(300).putInt(0).array(),request);
                    byte[] reply=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(7).putInt(16).putInt(300).putInt(320).array();
                    for(byte b:reply) { s.getOutputStream().write(b);s.getOutputStream().flush(); }
                    assertEquals(16,s.getInputStream().readNBytes(16).length);
                    s.getOutputStream().write(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(4).putInt(8).putInt(1).putInt(-2).array());
                    assertEquals(16,s.getInputStream().readNBytes(16).length);
                    s.getOutputStream().write(new byte[16]);
                } catch(IOException e) { throw new UncheckedIOException(e); }
            });
            try(var client=new PigpioClient("127.0.0.1",server.getLocalPort())) {
                assertEquals(320,client.frequency(16,300));assertThrows(IOException.class,()->client.write(8,true));
                assertDoesNotThrow(()->client.pwm(16,0));
            }
            peer.get(5,TimeUnit.SECONDS);
        }
    }
    @Test void rejectsUnconfirmedOverlappingPinsAndMissingServoMapping() {
        Properties p=new Properties();p.setProperty("backend","pigpio");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
        p.setProperty("hardware.confirmed","true");p.setProperty("servo.enabled","true");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
        p.setProperty("servo.pin","8");p.setProperty("buzzer.enabled","true");p.setProperty("buzzer.activeHigh","false");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
    }
    @Test void frequencyMismatchNeverEnablesMotorDuty() {
        var gpio=new Fake();gpio.hz=320;var hardware=new LinuxHardware(gpio,config(),new Audit());
        assertThrows(UncheckedIOException.class,()->hardware.action(1,"wheel.bind",true));
        assertFalse(gpio.calls.stream().anyMatch(s->s.startsWith("pwm") && !s.endsWith(":0")));
        assertTrue(gpio.closed);assertThrows(IllegalStateException.class,()->hardware.action(2,"wheel.speed",Map.of("left",40,"right",40)));
        hardware.close();
    }
    @Test void reverseStopServoAndReleaseUseConfiguredPins() {
        var gpio=new Fake();var hardware=new LinuxHardware(gpio,config(),new Audit());
        hardware.action(1,"wheel.bind",true);hardware.action(1,"wheel.speed",Map.of("left",-35,"right",20));
        assertTrue(gpio.calls.contains("write:21:false"));assertTrue(gpio.calls.contains("write:20:true"));assertTrue(gpio.calls.contains("pwm:16:35"));
        hardware.action(2,"servo.pulseMs",1.5);assertTrue(gpio.calls.contains("servo:18:1500"));
        hardware.action(3,"buzzer.enabled",true);assertTrue(gpio.calls.contains("write:8:false"));
        hardware.close();assertTrue(gpio.calls.contains("servo:18:0"));assertTrue(gpio.calls.contains("mode:16:0"));assertTrue(gpio.closed);
    }
    @Test void sameDirectionSpeedUpdatesDoNotPulseStopBetweenFrames() {
        var gpio=new Fake();
        try(var hardware=new LinuxHardware(gpio,config(),new Audit())) {
            hardware.action(1,"wheel.bind",true);
            hardware.action(2,"wheel.speed",Map.of("left",20,"right",20));gpio.calls.clear();
            hardware.action(3,"wheel.speed",Map.of("left",30,"right",30));
            assertEquals(List.of("pwm:16:30","pwm:13:30"),gpio.calls);
        }
    }
    private HardwareConfig config() {
        Properties p=new Properties();p.setProperty("backend","pigpio");p.setProperty("hardware.confirmed","true");p.setProperty("wheel.enabled","true");
        p.setProperty("left.forwardHigh","true");p.setProperty("right.forwardHigh","true");p.setProperty("servo.enabled","true");p.setProperty("servo.pin","18");
        p.setProperty("buzzer.enabled","true");p.setProperty("buzzer.activeHigh","false");return HardwareConfig.load(p);
    }
    static class Audit implements Hardware { public void action(long id,String action,Object value){} public void close(){} }
    static class Fake implements GpioBackend {
        List<String> calls=new ArrayList<>();int hz=300;boolean closed;
        public void mode(int p,int m){calls.add("mode:"+p+":"+m);}
        public void write(int p,boolean high){calls.add("write:"+p+":"+high);}
        public int frequency(int p,int n){calls.add("frequency:"+p+":"+n);return hz;}
        public void range(int p,int n){calls.add("range:"+p+":"+n);}
        public void pwm(int p,int n){calls.add("pwm:"+p+":"+n);}
        public void servo(int p,int n){calls.add("servo:"+p+":"+n);}
        public void close(){closed=true;}
    }
}
