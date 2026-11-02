package org.mark.carpi.usecase

import org.mark.carpi.model.Scheduler
import org.mark.carpi.repository.CarRepository

/** 摄像头舵机：progress 0..100 -> 0.8..1.5ms 脉冲；最后一次指令后 500ms 以触发指令 id 自动释放。 */
class MoveServoUseCase(private val lock: MotionLock, private val repository: CarRepository, private val scheduler: Scheduler) {
    operator fun invoke(id: Long, progress: Int) {
        lock.acquire(id, MotionLock.Device.SERVO)
        lock.cancelPendingServoRelease()
        repository.servoPulseMs(id, 0.8 + 0.007 * progress)
        lock.setPendingServoRelease(scheduler.schedule(RELEASE_MS) { lock.releaseIfOwner(id, MotionLock.Device.SERVO) })
    }

    companion object {
        const val RELEASE_MS = 500L
    }
}
