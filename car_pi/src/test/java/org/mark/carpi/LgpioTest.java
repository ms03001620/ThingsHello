package org.mark.carpi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mark.carpi.hardware.linux.*;
import org.mark.carpi.control.CommandDispatcher;
import org.mark.carpi.hardware.Hardware;
import org.mark.carpi.protocol.FrameCodec.Frame;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LgpioTest {
    @TempDir Path temporary;
    private HardwareConfig config() {
        var p=new Properties();p.setProperty("backend","lgpio");p.setProperty("hardware.confirmed","true");
        p.setProperty("wheel.enabled","true");p.setProperty("left.forwardHigh","false");p.setProperty("right.forwardHigh","false");
        p.setProperty("buzzer.enabled","true");p.setProperty("buzzer.activeHigh","false");return HardwareConfig.load(p);
    }
    private Map<String,String> fakeLibrary() throws Exception {
        Files.writeString(temporary.resolve("lgpio.py"),"""
            import os
            SET_PULL_UP, SET_PULL_DOWN, TX_PWM = 32,64,0
            active=set()
            def record(text):
                with open(os.environ['FAKE_LGPIO_LOG'],'a') as f:f.write(text+'\\n')
            def gpiochip_open(chip):return 1
            def gpio_claim_output(h,p,v,flags):record('claim:%d:%d'%(p,v));return 0
            def gpio_write(h,p,v):record('write:%d:%d'%(p,v));return 0
            def tx_busy(h,p,kind):return int(p in active)
            def tx_pwm(h,p,hz,duty):
                assert hz>0 and duty>0
                active.add(p);record('pwm:%d:%d:%d'%(p,hz,duty));return 9
            def tx_pulse(h,p,on,off):
                if p not in active:raise RuntimeError('bad PWM micros')
                active.remove(p);record('stop:%d'%p);return 0
            def gpio_free(h,p):
                assert p not in active
                record('free:%d'%p);return 0
            def gpiochip_close(h):
                assert not active
                record('closed');return 0
            """);
        return Map.of("PYTHONPATH",temporary.toString(),"FAKE_LGPIO_LOG",temporary.resolve("calls").toString());
    }
    @Test void stopsBothIdleAndRunningPwmAndReapsWorker() throws Exception {
        var client=new LgpioClient(config(),fakeLibrary());
        try {
            client.pwm(16,0); // Must not issue the library's invalid zero-pulse operation before PWM exists.
            assertEquals(300,client.frequency(16,300));client.pwm(16,40);client.pwm(16,0);
            client.write(8,false); // Active-low buzzer is initially claimed inactive-high.
            client.pwm(13,25); // close() must stop this still-running output.
        } finally { client.close(); }
        assertFalse(client.isAlive());
        String calls=Files.readString(temporary.resolve("calls"));
        assertTrue(calls.contains("pwm:16:300:40"));assertTrue(calls.contains("stop:13"));
        assertTrue(calls.contains("claim:8:1"));assertTrue(calls.indexOf("stop:13")<calls.indexOf("free:13"));
        assertTrue(calls.endsWith("closed\n"));
    }
    @Test void refusesUnconfiguredPinWithoutLosingCleanupChannel() throws Exception {
        try(var client=new LgpioClient(config(),fakeLibrary())) {
            assertThrows(java.io.IOException.class,()->client.write(18,true));
            assertDoesNotThrow(()->client.pwm(16,0));
        }
    }
    @Test void disconnectReleasesMotorBeforeNextCommand() throws Exception {
        List<String> calls=Collections.synchronizedList(new ArrayList<>());
        Hardware driver=new Hardware() { public void action(long id,String action,Object value){calls.add(action);}public void close(){} };
        try(var dispatcher=new CommandDispatcher(driver)) {
            dispatcher.submit(new Frame(0,"{\"left\":40,\"right\":40}"),f->{}).get();
            dispatcher.releaseControl().get();
            assertEquals("wheel.release",calls.get(calls.size()-1));
            dispatcher.submit(new Frame(0,"{\"left\":0,\"right\":0}"),f->{}).get();
            assertEquals(2,calls.stream().filter("wheel.bind"::equals).count());
        }
    }
    @Test void hardwareServoUsesSysfsAndReleasesOnTimeoutAndDisconnect() throws Exception {
        var base=config();
        var c=new HardwareConfig(base.backend(),base.host(),base.port(),base.left(),base.right(),
            base.lights(),base.lightHigh(),base.buzzer(),base.buzzerHigh(),18,base.frequency(),base.maxError());
        var env=new HashMap<>(fakeLibrary());
        env.put("CAR_PI_PWM_PATH",temporary.resolve("missing-pwm").toString());
        assertThrows(java.io.IOException.class,()->new LgpioClient(c,env));
        Path pwm=Files.createDirectory(temporary.resolve("pwm"));
        for(String name:List.of("enable","duty_cycle","period"))Files.writeString(pwm.resolve(name),"0");
        env.put("CAR_PI_PWM_PATH",pwm.toString());
        Hardware audit=new Hardware(){public void action(long id,String action,Object value){}public void close(){}};
        try(var client=new LgpioClient(c,env)) {
            assertEquals("20000000",Files.readString(pwm.resolve("period")));
            assertThrows(java.io.IOException.class,()->client.servo(18,1501));
            try(var dispatcher=new CommandDispatcher(new LinuxHardware(client,c,audit))) {
                dispatcher.submit(new Frame(4,"{\"progress\":50}"),f->{}).get();
                assertEquals("1150000",Files.readString(pwm.resolve("duty_cycle")));
                assertEquals("1",Files.readString(pwm.resolve("enable")));
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
                while(!Files.readString(pwm.resolve("enable")).equals("0") && System.nanoTime()<deadline)Thread.sleep(20);
                assertEquals("0",Files.readString(pwm.resolve("enable")));
                dispatcher.submit(new Frame(4,"{\"progress\":100}"),f->{}).get();
                assertEquals("1500000",Files.readString(pwm.resolve("duty_cycle")));
                dispatcher.releaseControl().get();
                assertEquals("0",Files.readString(pwm.resolve("enable")));
                dispatcher.submit(new Frame(4,"{\"progress\":0}"),f->{}).get();
            }
            assertEquals("0",Files.readString(pwm.resolve("enable")));
        }
        assertFalse(Files.readString(temporary.resolve("calls")).contains("claim:18:"));
    }

}
