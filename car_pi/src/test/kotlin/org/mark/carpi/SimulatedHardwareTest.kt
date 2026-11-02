package org.mark.carpi

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.hardware.simulated.SimulatedHardware
import java.nio.file.Files
import java.nio.file.Path

class SimulatedHardwareTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun simulatedBackendAppendsActionsWithRecordingLabel() {
        val log = temporary.resolve("logs/actions.jsonl")
        SimulatedHardware.logged(log).use { it.wheelSpeed(1, 40, 40) }
        SimulatedHardware.logged(log).use { it.wheelRelease(2) }
        val events = Files.readAllLines(log).map { JsonParser.parseString(it).asJsonObject }
        assertEquals(2, events.size)
        assertTrue(events.all { it.get("mode").asString == "simulated" })
        assertEquals(40, events.first().getAsJsonObject("value").get("left").asInt)
        assertEquals("wheel.release", events.last().get("action").asString)
        assertEquals(2L, events.last().get("command").asLong)
        assertFalse(events.first().get("time").asString.isBlank())
    }
}
