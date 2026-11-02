package org.mark.carpi.usecase

import org.mark.carpi.repository.CarRepository

/** 前灯开关。 */
class SetLightUseCase(private val repository: CarRepository) {
    operator fun invoke(id: Long, on: Boolean) = repository.light(id, on)
}
