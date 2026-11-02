package org.mark.carpi.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mark.carpi.FakeScheduler
import org.mark.carpi.recordingRepository

class MotionLockTest {
    private val log = mutableListOf<String>()
    private val repo = recordingRepository { command, action, value -> log += "$command $action $value" }
    private val sch = FakeScheduler()
    private val lock = MotionLock(repo)

    @Test
    fun wheelTakesOverFromServoAndCancelsTimer() {
        MoveServoUseCase(lock, repo, sch)(1, 0)
        DriveWheelUseCase(lock, repo)(2, 5, -5)
        sch.advance(1000)
        assertEquals(
            listOf("1 servo.bind true", "1 servo.pulseMs 0.8", "2 servo.release true", "2 wheel.bind true", "2 wheel.speed {left=5, right=-5}"),
            log,
        )
    }
}
