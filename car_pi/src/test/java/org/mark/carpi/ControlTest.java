package org.mark.carpi;
import org.junit.jupiter.api.Test;
import org.mark.carpi.control.CommandDispatcher;
import org.mark.carpi.hardware.Hardware;
import org.mark.carpi.protocol.FrameCodec.Frame;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ControlTest {
    record Event(long id,String action,Object value,long time) {}
    static class Recorder implements Hardware {
        final List<Event> events=new CopyOnWriteArrayList<>();
        public void action(long id,String action,Object value){events.add(new Event(id,action,value,System.nanoTime()));}
        public void close(){}
    }
    @Test void switchingToWheelsCancelsOldServoRelease() throws Exception {
        var r=new Recorder();
        try(var d=new CommandDispatcher(r)) {
            d.submit(new Frame(4,"{\"progress\":0}"),f->{}).get();
            d.submit(new Frame(0,"{\"left\":20,\"right\":20}"),f->{}).get();
            Thread.sleep(650);
            assertEquals(1,r.events.stream().filter(e->e.action.equals("servo.release")).count());
            assertTrue(r.events.stream().anyMatch(e->e.action.equals("servo.release") && e.id==2));
            assertFalse(r.events.stream().anyMatch(e->e.action.equals("wheel.release")));
        }
    }
    @Test void repeatedServoCommandResetsReleaseDeadline() throws Exception {
        var r=new Recorder();
        try(var d=new CommandDispatcher(r)) {
            d.submit(new Frame(4,"{\"progress\":0}"),f->{}).get();
            Thread.sleep(200);
            d.submit(new Frame(4,"{\"progress\":50}"),f->{}).get();
            Thread.sleep(350);
            assertFalse(r.events.stream().anyMatch(e->e.action.equals("servo.release")));
            Thread.sleep(350);
            Event pulse=r.events.stream().filter(e->e.action.equals("servo.pulseMs") && e.id==2).findFirst().orElseThrow();
            Event release=r.events.stream().filter(e->e.action.equals("servo.release")).findFirst().orElseThrow();
            assertEquals(2,release.id); assertTrue(release.time-pulse.time>=480_000_000L);
            assertEquals(1.15,((Number)pulse.value).doubleValue(),0.00001);
        }
    }
}
