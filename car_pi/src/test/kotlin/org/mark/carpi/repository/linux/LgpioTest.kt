package org.mark.carpi.repository.linux

import org.mark.carpi.send
import org.mark.carpi.releaseBlocking
import org.mark.carpi.recordingRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.viewmodel.CarViewModel
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LgpioClient
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.ActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.protocol.FrameCodec.Frame
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Properties
import java.util.concurrent.TimeUnit

class LgpioTest {
    @TempDir
    lateinit var temporary: Path

    private fun config(): HardwareConfig = HardwareConfig.load(Properties().apply {
        setProperty("hardware.mode", "real"); setProperty("hardware.confirmed", "true")
        setProperty("wheel.enabled", "true"); setProperty("left.forwardHigh", "false"); setProperty("right.forwardHigh", "false")
        setProperty("buzzer.enabled", "true"); setProperty("buzzer.activeHigh", "false")
    })

    private fun fakeLibrary(): Map<String, String> {
        Files.writeString(temporary.resolve("lgpio.py"), """
            import os
            SET_PULL_UP, SET_PULL_DOWN, TX_PWM = 32,64,0
            active=set()
            def record(text):
                with open(os.environ['FAKE_LGPIO_LOG'],'a') as f:f.write(text+'\n')
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
            """.trimIndent() + "\n")
        return mapOf("PYTHONPATH" to temporary.toString(), "FAKE_LGPIO_LOG" to temporary.resolve("calls").toString())
    }

    @Test
    fun stopsBothIdleAndRunningPwmAndReapsWorker() {
        val client = LgpioClient(config(), fakeLibrary())
        try {
            client.pwm(16, 0) // Must not issue the library's invalid zero-pulse operation before PWM exists.
            assertEquals(300, client.frequency(16, 300)); client.pwm(16, 40); client.pwm(16, 0)
            client.write(8, false) // Active-low buzzer is initially claimed inactive-high.
            client.pwm(13, 25) // close() must stop this still-running output.
        } finally {
            client.close()
        }
        assertFalse(client.isAlive)
        val calls = Files.readString(temporary.resolve("calls"))
        assertTrue(calls.contains("pwm:16:300:40")); assertTrue(calls.contains("stop:13"))
        assertTrue(calls.contains("claim:8:1")); assertTrue(calls.indexOf("stop:13") < calls.indexOf("free:13"))
        assertTrue(calls.endsWith("closed\n"))
    }

    @Test
    fun refusesUnconfiguredPinWithoutLosingCleanupChannel() {
        LgpioClient(config(), fakeLibrary()).use { client ->
            assertThrows(IOException::class.java) { client.write(18, true) }
            assertDoesNotThrow(Executable { client.pwm(16, 0) })
        }
    }

    @Test
    fun disconnectReleasesMotorBeforeNextCommand() {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val driver = recordingRepository { _, action, _ -> calls.add(action) }
        CarViewModel.of(driver).use { viewModel ->
            viewModel.send(0, """{"left":40,"right":40}""")
            viewModel.releaseBlocking()
            assertEquals("wheel.release", calls.last())
            viewModel.send(0, """{"left":0,"right":0}""")
            assertEquals(2, calls.count { it == "wheel.bind" })
        }
    }

    @Test
    fun missingPwmDegradesServoOnlyWithoutBreakingWheels() {
        val c = config().copy(servo = 18)
        val env = fakeLibrary() + ("CAR_PI_PWM_PATH" to temporary.resolve("missing-pwm").toString())
        val recorder = ActionLogRecorder { _, _, _ -> }
        LgpioClient(c, env).use { client ->
            assertThrows(IOException::class.java) { client.servo(18, 1501) }
            CarViewModel.of(LoggingCarRepository(LinuxCarRepository(client, c, recorder::record), recorder)).use { viewModel ->
                viewModel.send(4, """{"progress":50}""")
                viewModel.send(0, """{"left":40,"right":40}""")
            }
        }
        val calls = Files.readString(temporary.resolve("calls"))
        assertTrue(calls.contains("pwm:16:300:40"))
        assertTrue(calls.contains("pwm:13:300:40"))
        assertFalse(calls.contains("claim:18:"))
        assertTrue(calls.endsWith("closed\n"))
    }

    @Test
    fun hardwareServoUsesSysfsAndReleasesOnTimeoutAndDisconnect() {
        val c = config().copy(servo = 18)
        val pwm = Files.createDirectory(temporary.resolve("pwm"))
        for (name in listOf("enable", "duty_cycle", "period")) Files.writeString(pwm.resolve(name), "0")
        val env = fakeLibrary() + ("CAR_PI_PWM_PATH" to pwm.toString())
        val recorder = ActionLogRecorder { _, _, _ -> }
        LgpioClient(c, env).use { client ->
            assertEquals("20000000", Files.readString(pwm.resolve("period")))
            assertThrows(IOException::class.java) { client.servo(18, 1501) }
            CarViewModel.of(LoggingCarRepository(LinuxCarRepository(client, c, recorder::record), recorder)).use { viewModel ->
                viewModel.send(4, """{"progress":50}""")
                assertEquals("1150000", Files.readString(pwm.resolve("duty_cycle")))
                assertEquals("1", Files.readString(pwm.resolve("enable")))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (Files.readString(pwm.resolve("enable")) != "0" && System.nanoTime() < deadline) Thread.sleep(20)
                assertEquals("0", Files.readString(pwm.resolve("enable")))
                viewModel.send(4, """{"progress":100}""")
                assertEquals("1500000", Files.readString(pwm.resolve("duty_cycle")))
                viewModel.releaseBlocking()
                assertEquals("0", Files.readString(pwm.resolve("enable")))
                viewModel.send(4, """{"progress":0}""")
            }
            assertEquals("0", Files.readString(pwm.resolve("enable")))
        }
        assertFalse(Files.readString(temporary.resolve("calls")).contains("claim:18:"))
    }
}
