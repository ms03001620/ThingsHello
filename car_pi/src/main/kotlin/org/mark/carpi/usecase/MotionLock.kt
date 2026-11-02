package org.mark.carpi.usecase

import org.mark.carpi.model.Cancellable
import org.mark.carpi.repository.CarRepository

/**
 * 运动部件独占（车轮 / 舵机同一时刻只有一个 owner）。切换 owner 时先取消待执行的舵机自动释放，
 * 再释放旧 owner、绑定新 owner；所有动作都经 CarRepository 记录。单线程使用（无锁）。
 */
class MotionLock(private val repository: CarRepository) {
    enum class Device { WHEEL, SERVO }

    var owner: Device? = null
        private set
    private var pendingServoRelease: Cancellable? = null

    /** 让 [device] 成为 owner；已是 owner 时为空操作。 */
    fun acquire(id: Long, device: Device) {
        if (device == owner) return
        cancelPendingServoRelease()
        owner?.let { release(id, it) }
        owner = null
        if (device == Device.WHEEL) repository.wheelBind(id) else repository.servoBind(id)
        owner = device
    }

    /** 记录新的舵机自动释放任务（替换前需先 [cancelPendingServoRelease]）。 */
    fun setPendingServoRelease(task: Cancellable) { pendingServoRelease = task }

    fun cancelPendingServoRelease() { pendingServoRelease?.cancel() }

    /** 定时释放：仅当 [device] 仍是 owner 时释放。 */
    fun releaseIfOwner(id: Long, device: Device) {
        if (owner == device) {
            release(id, device)
            owner = null
        }
    }

    /** 释放当前 owner（断开 / 关闭，id=0）；不取消定时器，由调用方按顺序处理。 */
    fun releaseOwner(id: Long) {
        try {
            owner?.let { release(id, it) }
        } finally {
            owner = null
        }
    }

    private fun release(id: Long, device: Device) =
        if (device == Device.WHEEL) repository.wheelRelease(id) else repository.servoRelease(id)
}
