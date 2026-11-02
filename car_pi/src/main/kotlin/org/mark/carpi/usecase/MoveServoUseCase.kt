package org.mark.carpi.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mark.carpi.repository.CarRepository

/**
 * 摄像头舵机：progress 0..100 -> 0.8..1.5ms 脉冲；最后一次指令后 500ms 以触发指令 id 自动释放。
 * 定时器 = [timers] 中的 delay 协程（应是 CarSession 的单线程作用域），取消 = Job.cancel。
 */
class MoveServoUseCase(private val lock: MotionLock, private val repository: CarRepository, private val timers: CoroutineScope) {
    operator fun invoke(id: Long, progress: Int) {
        lock.acquire(id, MotionLock.Device.SERVO)
        lock.cancelPendingServoRelease()
        repository.servoPulseMs(id, 0.8 + 0.007 * progress)
        // UNDISPATCHED：立即开始计时（截止时间从本指令执行时算起）
        lock.setPendingServoRelease(timers.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(RELEASE_MS)
            lock.releaseIfOwner(id, MotionLock.Device.SERVO)
        })
    }

    companion object {
        const val RELEASE_MS = 500L
    }
}
