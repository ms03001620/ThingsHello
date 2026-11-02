package org.mark.carpi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mark.carpi.recordingHardware

class CarControllerTest {
    class FakeScheduler : Scheduler {
        private class Task(val at: Long, val run: () -> Unit) { var cancelled = false }
        private val tasks = mutableListOf<Task>()
        var now = 0L
        override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
            val t = Task(now + delayMs, task); tasks += t
            return Cancellable { t.cancelled = true }
        }
        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                tasks -= next; now = next.at; next.run()
            }
            now = target
        }
    }

    private val log = mutableListOf<String>()
    private val hw = recordingHardware { command, action, value -> log += "$command $action $value" }
    private val sch = FakeScheduler()
    private val c = CarController(hw, hw, sch)
    private fun actions() = log.filterNot { it.contains(" command ") }

    @Test
    fun servoReleasesAfter500msWithTriggeringId() {
        c.handle(4, "b", Command.Servo(0))
        sch.advance(499)
        assertEquals(listOf("1 servo.bind true", "1 servo.pulseMs 0.8"), actions())
        sch.advance(1)
        assertEquals("1 servo.release true", actions().last())
    }

    @Test
    fun repeatedServoResetsDeadline() {
        c.handle(4, "b", Command.Servo(0)); sch.advance(400)
        c.handle(4, "b", Command.Servo(50)); sch.advance(400)
        assertEquals(0, actions().count { it.contains("servo.release") })
        sch.advance(100)
        assertEquals("2 servo.release true", actions().last())
    }

    @Test
    fun wheelTakesOverFromServoAndCancelsTimer() {
        c.handle(4, "b", Command.Servo(0))
        c.handle(0, "b", Command.Wheel(5, -5))
        sch.advance(1000)
        assertEquals(
            listOf("1 servo.bind true", "1 servo.pulseMs 0.8", "2 servo.release true", "2 wheel.bind true", "2 wheel.speed {left=5, right=-5}"),
            actions(),
        )
    }

    @Test
    fun buzzerStopsAfter500ms() {
        c.handle(2, "b", Command.Beep(true)); sch.advance(500)
        c.handle(2, "b", Command.Beep(false)); sch.advance(500)
        assertEquals(listOf("1 buzzer.enabled true", "1 buzzer.enabled false"), actions())
    }

    @Test
    fun invalidAndUnsupportedStillIncrementSequence() {
        c.handle(9, "x", Command.Invalid("boom"))
        c.handle(3, "{}", Command.Unsupported(3))
        assertEquals(listOf("1 command {type=9, body=x}", "1 rejected boom", "2 command {type=3, body={}}", "2 unsupported.type 3"), log)
    }

    @Test
    fun rotateAndReleaseControlAndShutdown() {
        c.handle(6, "b", Command.Rotate(Command.Direction.LEFT, 40))
        c.releaseControl(); c.releaseControl()
        c.handle(4, "b", Command.Servo(100))
        c.beep(0, 10_000)
        c.shutdown(); sch.advance(20_000)
        assertEquals(
            listOf("1 wheel.bind true", "1 wheel.speed {left=-40, right=40}", "0 wheel.release true",
                "2 servo.bind true", "2 servo.pulseMs 1.5", "0 buzzer.enabled true", "0 servo.release true", "0 all.stop true"),
            actions(),
        )
    }

    @Test
    fun hardwareFailureIsRejectedWithToString() {
        val badLog = mutableListOf<String>()
        val failing = object : CarHardware by org.mark.carpi.hardware.simulated.SimulatedHardware() {
            override fun light(id: Long, on: Boolean) = throw IllegalStateException("faulted")
        }
        val bad = recordingHardware(failing) { command, action, value -> badLog += "$command $action $value" }
        CarController(bad, bad, sch).handle(1, "b", Command.Light(true))
        // 装饰器先记录再委托：失败的动作行仍在，随后是 rejected
        assertEquals(listOf("1 light.rgb [100, 100, 100]", "1 rejected java.lang.IllegalStateException: faulted"), badLog.takeLast(2))
    }
}
