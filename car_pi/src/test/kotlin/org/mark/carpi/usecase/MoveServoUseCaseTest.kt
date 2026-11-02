package org.mark.carpi.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mark.carpi.FakeScheduler
import org.mark.carpi.recordingRepository

class MoveServoUseCaseTest {
    private val log = mutableListOf<String>()
    private val repo = recordingRepository { command, action, value -> log += "$command $action $value" }
    private val sch = FakeScheduler()
    private val moveServo = MoveServoUseCase(MotionLock(repo), repo, sch)

    @Test
    fun servoReleasesAfter500msWithTriggeringId() {
        moveServo(1, 0)
        sch.advance(499)
        assertEquals(listOf("1 servo.bind true", "1 servo.pulseMs 0.8"), log)
        sch.advance(1)
        assertEquals("1 servo.release true", log.last())
    }

    @Test
    fun repeatedServoResetsDeadline() {
        moveServo(1, 0); sch.advance(400)
        moveServo(2, 50); sch.advance(400)
        assertEquals(0, log.count { it.contains("servo.release") })
        sch.advance(100)
        assertEquals("2 servo.release true", log.last())
    }
}
