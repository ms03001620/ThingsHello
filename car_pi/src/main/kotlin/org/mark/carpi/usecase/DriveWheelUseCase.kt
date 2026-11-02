package org.mark.carpi.usecase

import org.mark.carpi.repository.CarRepository

/** 车轮差速：取得车轮 owner 后下发左右速度。 */
class DriveWheelUseCase(private val lock: MotionLock, private val repository: CarRepository) {
    operator fun invoke(id: Long, left: Int, right: Int) {
        lock.acquire(id, MotionLock.Device.WHEEL)
        repository.wheelSpeed(id, left, right)
    }
}
