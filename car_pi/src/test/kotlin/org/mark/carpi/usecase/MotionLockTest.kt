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
        MoveServoUseCase(lock, repo, sch.scope)(1, 0)
        DriveWheelUseCase(lock, repo)(2, 5, -5)
        sch.advance(1000)
        assertEquals(
            listOf("1 servo.bind true", "1 servo.pulseMs 0.8", "2 servo.release true", "2 wheel.bind true", "2 wheel.speed {left=5, right=-5}"),
            log,
        )
    }

    @Test
    fun failedTimedServoReleaseStillClearsOwner() {
        val failing = object : org.mark.carpi.repository.CarRepository by org.mark.carpi.repository.simulated.SimulatedCarRepository() {
            override fun servoRelease(id: Long) = throw IllegalStateException("faulted")
        }
        val failLog = mutableListOf<String>()
        val repo = recordingRepository(failing) { command, action, value -> failLog += "$command $action $value" }
        val lock = MotionLock(repo)
        MoveServoUseCase(lock, repo, sch.scope)(1, 0)
        sch.advance(500) // 定时释放抛异常（由 CarViewModel 的 CoroutineExceptionHandler 告警）
        assertEquals(null, lock.owner)
        lock.releaseOwner(0) // owner 已清空：断开/关闭时不再重复释放舵机
        assertEquals(listOf("1 servo.bind true", "1 servo.pulseMs 0.8", "1 servo.release true"), failLog)
    }
}
