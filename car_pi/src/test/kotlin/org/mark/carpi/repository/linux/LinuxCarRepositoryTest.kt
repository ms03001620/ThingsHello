package org.mark.carpi.repository.linux

import org.mark.carpi.recordingRepository
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.session.CarSession
import org.mark.carpi.runtime.carSession
import org.mark.carpi.repository.linux.GpioBackend
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.HardwareMode
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class LinuxCarRepositoryTest {
    @TempDir
    lateinit var temporary: Path

    private fun props(vararg entries: Pair<String, String>) = Properties().apply { entries.forEach { (k, v) -> setProperty(k, v) } }

    @Test
    fun hardwareModeDefaultsToSimulatedAndRejectsRemovedBackendKey() {
        assertEquals(HardwareMode.SIMULATED, HardwareConfig.load(Properties()).mode)
        assertEquals(HardwareMode.SIMULATED, HardwareConfig.load(props("hardware.mode" to "simulated")).mode)
        val real = props("hardware.mode" to "real", "hardware.confirmed" to "true", "buzzer.enabled" to "true", "buzzer.activeHigh" to "true")
        assertTrue(HardwareConfig.load(real).real)
        assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(props("hardware.mode" to "lgpio")) }
        for (removed in listOf("recording", "lgpio", "pigpio"))
            assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(props("backend" to removed)) }
    }

    @Test
    fun startupBeepDurationIsConfigurable() {
        assertEquals(1000, HardwareConfig.load(Properties()).startupBeepMs)
        assertEquals(0, HardwareConfig.load(props("buzzer.startupMs" to "0")).startupBeepMs)
        assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(props("buzzer.startupMs" to "-1")) }
    }

    @Test
    fun startupBeepTurnsBuzzerOnThenOffAndCloseSilencesIt() {
        val gpio = Fake()
        val session = carSession(recordingRepository(LinuxCarRepository(gpio, config()) { _, _, _ -> }) { _, _, _ -> })
        session.beep(50).get()
        assertEquals("write:8:false", gpio.calls.last())
        Thread.sleep(300)
        assertEquals("write:8:true", gpio.calls.last())
        session.beep(10000).get(); gpio.calls.clear()
        session.close()
        assertTrue(gpio.calls.contains("write:8:true")); assertTrue(gpio.closed)
    }

    @Test
    fun rejectsUnconfirmedOverlappingPinsAndMissingServoMapping() {
        val p = props("hardware.mode" to "real")
        assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(p) }
        p.setProperty("hardware.confirmed", "true"); p.setProperty("servo.enabled", "true")
        assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(p) }
        p.setProperty("servo.pin", "18"); p.setProperty("buzzer.enabled", "true"); p.setProperty("buzzer.pin", "18"); p.setProperty("buzzer.activeHigh", "false")
        assertThrows(IllegalArgumentException::class.java) { HardwareConfig.load(p) }
    }

    @Test
    fun frequencyMismatchNeverEnablesMotorDuty() {
        val gpio = Fake().apply { hz = 320 }
        val hardware = LinuxCarRepository(gpio, config()) { _, _, _ -> }
        assertThrows(UncheckedIOException::class.java) { hardware.wheelBind(1) }
        assertFalse(gpio.calls.any { it.startsWith("pwm") && !it.endsWith(":0") })
        assertTrue(gpio.closed)
        assertThrows(IllegalStateException::class.java) { hardware.wheelSpeed(2, 40, 40) }
        hardware.close()
    }

    @Test
    fun reverseStopServoAndReleaseUseConfiguredPins() {
        val log = temporary.resolve("actions.jsonl")
        val gpio = Fake()
        val recorder = JsonlActionLogRecorder(log, "real")
        val hardware = LoggingCarRepository(LinuxCarRepository(gpio, config(), recorder::record), recorder)
        hardware.wheelBind(1); hardware.wheelSpeed(1, -35, 20)
        assertTrue(gpio.calls.contains("write:21:false")); assertTrue(gpio.calls.contains("write:20:true")); assertTrue(gpio.calls.contains("pwm:16:35"))
        hardware.servoPulseMs(2, 1.5); assertTrue(gpio.calls.contains("servo:18:1500"))
        hardware.buzzer(3, true); assertTrue(gpio.calls.contains("write:8:false"))
        hardware.close(); assertTrue(gpio.calls.contains("servo:18:0")); assertTrue(gpio.calls.contains("mode:16:0")); assertTrue(gpio.closed)
        val events = Files.readAllLines(log).map { JsonParser.parseString(it).asJsonObject }
        assertTrue(events.all { it.get("mode").asString == "real" })
        assertTrue(events.any { it.get("action").asString == "pwm.configuredHz" })
        val speed = events.first { it.get("action").asString == "wheel.speed" }
        assertEquals(1L, speed.get("command").asLong)
        assertEquals(-35, speed.getAsJsonObject("value").get("left").asInt)
    }

    @Test
    fun sameDirectionSpeedUpdatesDoNotPulseStopBetweenFrames() {
        val gpio = Fake()
        LinuxCarRepository(gpio, config()) { _, _, _ -> }.use { hardware ->
            hardware.wheelBind(1)
            hardware.wheelSpeed(2, 20, 20); gpio.calls.clear()
            hardware.wheelSpeed(3, 30, 30)
            assertEquals(listOf("pwm:16:30", "pwm:13:30"), gpio.calls)
        }
    }

    private fun config(): HardwareConfig = HardwareConfig.load(props(
        "hardware.mode" to "real", "hardware.confirmed" to "true", "wheel.enabled" to "true",
        "left.forwardHigh" to "true", "right.forwardHigh" to "true", "servo.enabled" to "true", "servo.pin" to "18",
        "buzzer.enabled" to "true", "buzzer.activeHigh" to "false",
    ))

    class Fake : GpioBackend {
        val calls = mutableListOf<String>()
        var hz = 300
        var closed = false
        override fun mode(pin: Int, mode: Int) { calls.add("mode:$pin:$mode") }
        override fun write(pin: Int, high: Boolean) { calls.add("write:$pin:$high") }
        override fun frequency(pin: Int, hz: Int): Int { calls.add("frequency:$pin:$hz"); return this.hz }
        override fun range(pin: Int, range: Int) { calls.add("range:$pin:$range") }
        override fun pwm(pin: Int, duty: Int) { calls.add("pwm:$pin:$duty") }
        override fun servo(pin: Int, microseconds: Int) { calls.add("servo:$pin:$microseconds") }
        override fun close() { closed = true }
    }
}
