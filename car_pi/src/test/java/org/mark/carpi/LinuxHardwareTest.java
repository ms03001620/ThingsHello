package org.mark.carpi;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mark.carpi.hardware.logging.JsonlActionLogRecorder;
import org.mark.carpi.hardware.linux.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LinuxHardwareTest {
    @TempDir Path temporary;
    @Test void hardwareModeDefaultsToSimulatedAndAcceptsLegacyBackendKey() {
        assertEquals(HardwareMode.SIMULATED,HardwareConfig.load(new Properties()).mode());
        Properties legacy=new Properties();legacy.setProperty("backend","recording");
        assertEquals(HardwareMode.SIMULATED,HardwareConfig.load(legacy).mode());
        Properties real=new Properties();real.setProperty("backend","lgpio");real.setProperty("hardware.confirmed","true");real.setProperty("buzzer.enabled","true");real.setProperty("buzzer.activeHigh","true");
        assertTrue(HardwareConfig.load(real).real());
        Properties bad=new Properties();bad.setProperty("hardware.mode","lgpio");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(bad));
        Properties old=new Properties();old.setProperty("backend","pigpio");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(old));
    }
    @Test void startupBeepDurationIsConfigurable() {
        assertEquals(1000,HardwareConfig.load(new Properties()).startupBeepMs());
        Properties off=new Properties();off.setProperty("buzzer.startupMs","0");
        assertEquals(0,HardwareConfig.load(off).startupBeepMs());
        Properties bad=new Properties();bad.setProperty("buzzer.startupMs","-1");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(bad));
    }
    @Test void startupBeepTurnsBuzzerOnThenOffAndCloseSilencesIt() throws Exception {
        var gpio=new Fake();
        var dispatcher=new org.mark.carpi.control.CommandDispatcher(new LinuxHardware(gpio,config(),(id,action,value)->{}));
        dispatcher.beep(50).get();
        assertEquals("write:8:false",gpio.calls.get(gpio.calls.size()-1));
        Thread.sleep(300);
        assertEquals("write:8:true",gpio.calls.get(gpio.calls.size()-1));
        dispatcher.beep(10000).get();gpio.calls.clear();
        dispatcher.close();
        assertTrue(gpio.calls.contains("write:8:true"));assertTrue(gpio.closed);
    }
    @Test void rejectsUnconfirmedOverlappingPinsAndMissingServoMapping() {
        Properties p=new Properties();p.setProperty("hardware.mode","real");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
        p.setProperty("hardware.confirmed","true");p.setProperty("servo.enabled","true");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
        p.setProperty("servo.pin","18");p.setProperty("buzzer.enabled","true");p.setProperty("buzzer.pin","18");p.setProperty("buzzer.activeHigh","false");
        assertThrows(IllegalArgumentException.class,()->HardwareConfig.load(p));
    }
    @Test void frequencyMismatchNeverEnablesMotorDuty() {
        var gpio=new Fake();gpio.hz=320;var hardware=new LinuxHardware(gpio,config(),(id,action,value)->{});
        assertThrows(UncheckedIOException.class,()->hardware.action(1,"wheel.bind",true));
        assertFalse(gpio.calls.stream().anyMatch(s->s.startsWith("pwm") && !s.endsWith(":0")));
        assertTrue(gpio.closed);assertThrows(IllegalStateException.class,()->hardware.action(2,"wheel.speed",Map.of("left",40,"right",40)));
        hardware.close();
    }
    @Test void reverseStopServoAndReleaseUseConfiguredPins() throws Exception {
        Path log=temporary.resolve("actions.jsonl");
        var gpio=new Fake();var hardware=new LinuxHardware(gpio,config(),new JsonlActionLogRecorder(log,"real"));
        hardware.action(1,"wheel.bind",true);hardware.action(1,"wheel.speed",Map.of("left",-35,"right",20));
        assertTrue(gpio.calls.contains("write:21:false"));assertTrue(gpio.calls.contains("write:20:true"));assertTrue(gpio.calls.contains("pwm:16:35"));
        hardware.action(2,"servo.pulseMs",1.5);assertTrue(gpio.calls.contains("servo:18:1500"));
        hardware.action(3,"buzzer.enabled",true);assertTrue(gpio.calls.contains("write:8:false"));
        hardware.close();assertTrue(gpio.calls.contains("servo:18:0"));assertTrue(gpio.calls.contains("mode:16:0"));assertTrue(gpio.closed);
        var events=Files.readAllLines(log).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
        assertTrue(events.stream().allMatch(event->event.get("mode").getAsString().equals("real")));
        assertTrue(events.stream().anyMatch(event->event.get("action").getAsString().equals("pwm.configuredHz")));
        var speed=events.stream().filter(event->event.get("action").getAsString().equals("wheel.speed")).findFirst().orElseThrow();
        assertEquals(1,speed.get("command").getAsLong());
        assertEquals(-35,speed.getAsJsonObject("value").get("left").getAsInt());
    }
    @Test void sameDirectionSpeedUpdatesDoNotPulseStopBetweenFrames() {
        var gpio=new Fake();
        try(var hardware=new LinuxHardware(gpio,config(),(id,action,value)->{})) {
            hardware.action(1,"wheel.bind",true);
            hardware.action(2,"wheel.speed",Map.of("left",20,"right",20));gpio.calls.clear();
            hardware.action(3,"wheel.speed",Map.of("left",30,"right",30));
            assertEquals(List.of("pwm:16:30","pwm:13:30"),gpio.calls);
        }
    }
    private HardwareConfig config() {
        Properties p=new Properties();p.setProperty("hardware.mode","real");p.setProperty("hardware.confirmed","true");p.setProperty("wheel.enabled","true");
        p.setProperty("left.forwardHigh","true");p.setProperty("right.forwardHigh","true");p.setProperty("servo.enabled","true");p.setProperty("servo.pin","18");
        p.setProperty("buzzer.enabled","true");p.setProperty("buzzer.activeHigh","false");return HardwareConfig.load(p);
    }
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
