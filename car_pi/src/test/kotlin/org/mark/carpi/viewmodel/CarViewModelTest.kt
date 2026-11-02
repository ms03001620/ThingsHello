package org.mark.carpi.viewmodel

import org.mark.carpi.send
import org.mark.carpi.beepBlocking
import org.mark.carpi.recordingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mark.carpi.viewmodel.CarViewModel
import org.mark.carpi.repository.protocol.FrameCodec.Frame
import org.mark.carpi.FakeScheduler
import org.mark.carpi.model.Command
import org.mark.carpi.repository.CarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import java.util.concurrent.CopyOnWriteArrayList

class CarViewModelTest {
    data class Event(val id: Long, val action: String, val value: Any?, val time: Long)

    class Recorder {
        val events = CopyOnWriteArrayList<Event>()
        val hardware = recordingRepository { command, action, value -> events.add(Event(command, action, value, System.nanoTime())) }
    }

    @Test
    fun switchingToWheelsCancelsOldServoRelease() {
        val r = Recorder()
        CarViewModel.of(r.hardware).use { d ->
            d.send(4, """{"progress":0}""")
            d.send(0, """{"left":20,"right":20}""")
            Thread.sleep(650)
            assertEquals(1, r.events.count { it.action == "servo.release" })
            assertTrue(r.events.any { it.action == "servo.release" && it.id == 2L })
            assertFalse(r.events.any { it.action == "wheel.release" })
        }
    }

    @Test
    fun repeatedServoCommandResetsReleaseDeadline() {
        val r = Recorder()
        CarViewModel.of(r.hardware).use { d ->
            d.send(4, """{"progress":0}""")
            Thread.sleep(200)
            d.send(4, """{"progress":50}""")
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

    // ---- 以下用例迁自原 CarControllerTest：虚拟时钟，直接在测试线程调用 handle ----

    private val log = mutableListOf<String>()
    private val repo = recordingRepository { command, action, value -> log += "$command $action $value" }
    private val sch = FakeScheduler()
    private fun actions() = log.filterNot { it.contains(" command ") }
    private fun fakeViewModel(r: org.mark.carpi.repository.LoggingCarRepository = repo) = CarViewModel(r, r, sch.dispatcher)

    @Test
    fun beepFalseIsNoopAndBeepTrueStopsAfter500ms() {
        fakeViewModel().use { s ->
            s.handle(2, "b", Command.Beep(true)); sch.advance(500)
            s.handle(2, "b", Command.Beep(false)); sch.advance(500)
            assertEquals(listOf("1 buzzer.enabled true", "1 buzzer.enabled false"), actions().take(2))
        }
    }

    @Test
    fun invalidAndUnsupportedStillIncrementSequence() {
        val s = fakeViewModel()
        s.handle(9, "x", Command.Invalid("boom"))
        s.handle(3, "{}", Command.Unsupported(3))
        assertEquals(listOf("1 command {type=9, body=x}", "1 rejected boom", "2 command {type=3, body={}}", "2 unsupported.type 3"), log)
        s.close()
    }

    @Test
    fun rotateAndReleaseControlAndShutdown() {
        val s = fakeViewModel()
        s.handle(6, "b", Command.Rotate(Command.Direction.LEFT, 40))
        s.releaseControlNow(); s.releaseControlNow()
        s.handle(4, "b", Command.Servo(100))
        s.beepBlocking(10_000)
        s.close(); sch.advance(20_000)
        assertEquals(
            listOf("1 wheel.bind true", "1 wheel.speed {left=-40, right=40}", "0 wheel.release true",
                "2 servo.bind true", "2 servo.pulseMs 1.5", "0 buzzer.enabled true", "0 servo.release true", "0 all.stop true"),
            actions(),
        )
    }

    @Test
    fun repositoryFailureIsRejectedWithToString() {
        val badLog = mutableListOf<String>()
        val failing = object : CarRepository by SimulatedCarRepository() {
            override fun light(id: Long, on: Boolean) = throw IllegalStateException("faulted")
        }
        val bad = recordingRepository(failing) { command, action, value -> badLog += "$command $action $value" }
        fakeViewModel(bad).use { it.handle(1, "b", Command.Light(true)) }
        // 装饰器先记录再委托：失败的动作行仍在，随后是 rejected
        assertEquals(listOf("1 light.rgb [100, 100, 100]", "1 rejected java.lang.IllegalStateException: faulted"), badLog.dropLast(1).takeLast(2))
    }

    @Test
    fun testDispatcherCreatesNoViewModelThread() {
        fun viewModelThreads() = Thread.getAllStackTraces().keys.count { it.name.startsWith("car-viewmodel") }
        val before = viewModelThreads()
        fakeViewModel().use { s ->
            s.handle(4, "b", Command.Servo(0))
            assertEquals(before, viewModelThreads())
        }
        assertEquals(before, viewModelThreads())
    }
}
