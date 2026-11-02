package org.mark.carpi;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mark.carpi.hardware.simulated.SimulatedHardware;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedHardwareTest {
    @TempDir Path temporary;

    @Test void simulatedBackendAppendsActionsWithRecordingLabel() throws Exception {
        Path log=temporary.resolve("logs/actions.jsonl");
        try(var hardware=new SimulatedHardware(log)) {
            hardware.action(1,"wheel.speed",Map.of("left",40,"right",40));
        }
        try(var hardware=new SimulatedHardware(log)) {
            hardware.action(2,"wheel.release",true);
        }
        var events=Files.readAllLines(log).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
        assertEquals(2,events.size());
        assertTrue(events.stream().allMatch(event->event.get("backend").getAsString().equals("recording")));
        assertEquals(40,events.getFirst().getAsJsonObject("value").get("left").getAsInt());
        assertEquals("wheel.release",events.getLast().get("action").getAsString());
        assertEquals(2,events.getLast().get("command").getAsLong());
        assertFalse(events.getFirst().get("time").getAsString().isBlank());
    }
}
