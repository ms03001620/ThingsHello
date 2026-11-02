package org.mark.carpi.usecase

import org.mark.carpi.model.Command
import org.mark.carpi.repository.CarRepository

/** 原地旋转：LEFT = (-s, s)，RIGHT = (s, -s)。 */
class RotateUseCase(private val lock: MotionLock, private val repository: CarRepository) {
    operator fun invoke(id: Long, direction: Command.Direction, speed: Int) {
        lock.acquire(id, MotionLock.Device.WHEEL)
        val left = direction == Command.Direction.LEFT
        repository.wheelSpeed(id, if (left) -speed else speed, if (left) speed else -speed)
    }
}
