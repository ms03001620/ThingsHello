package org.mark.carpi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mark.carpi.control.CommandDispatcher
import org.mark.carpi.protocol.FrameCodec.Frame
import java.util.concurrent.CopyOnWriteArrayList

class ControlTest {
    data class Event(val id: Long, val action: String, val value: Any?, val time: Long)

    class Recorder {
        val events = CopyOnWriteArrayList<Event>()
        val hardware = recordingHardware { command, action, value -> events.add(Event(command, action, value, System.nanoTime())) }
    }

    @Test
    fun switchingToWheelsCancelsOldServoRelease() {
        val r = Recorder()
        CommandDispatcher(r.hardware).use { d ->
            d.submit(Frame(4, """{"progress":0}""")) {}.get()
            d.submit(Frame(0, """{"left":20,"right":20}""")) {}.get()
            Thread.sleep(650)
            assertEquals(1, r.events.count { it.action == "servo.release" })
            assertTrue(r.events.any { it.action == "servo.release" && it.id == 2L })
            assertFalse(r.events.any { it.action == "wheel.release" })
        }
    }

    @Test
    fun repeatedServoCommandResetsReleaseDeadline() {
        val r = Recorder()
        CommandDispatcher(r.hardware).use { d ->
            d.submit(Frame(4, """{"progress":0}""")) {}.get()
            Thread.sleep(200)
            d.submit(Frame(4, """{"progress":50}""")) {}.get()
            Thread.sleep(350)
            assertFalse(r.events.any { it.action == "servo.release" })
            Thread.sleep(350)
            val pulse = r.events.first { it.action == "servo.pulseMs" && it.id == 2L }
            val release = r.events.first { it.action == "servo.release" }
            assertEquals(2L, release.id)
            assertTrue(release.time - pulse.time >= 480_000_000L)
            assertEquals(1.15, (pulse.value as Number).toDouble(), 0.00001)
        }
    }
}
