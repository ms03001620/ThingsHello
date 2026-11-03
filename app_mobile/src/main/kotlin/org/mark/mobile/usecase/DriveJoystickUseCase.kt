package org.mark.mobile.usecase

import org.mark.mobile.ctrl.WheelSpeed
import org.mark.mobile.repository.ICarControlRepository
import kotlin.math.roundToInt

/**
 * 摇杆控制 UseCase：处理死区急停、按限速百分比折算最大速度、计算左右轮差速并节流下发。
 */
class DriveJoystickUseCase(
    private val controlRepository: ICarControlRepository
) {
    companion object {
        private const val STICK_DEADZONE = 0.02f

        /**
         * 根据摇杆角度（0..360，90=前进，270=后退，180=左，0/1=右）换算左右两轮差速。
         */
        fun mixSpeed(angle: Int, max: Int): WheelSpeed {
            if (angle < 0 || max <= 0) return WheelSpeed(0, 0)
            val p = max / 90f
            fun step(deg: Int): Int = (deg * p).toInt()
            return when {
                angle <= 90 -> WheelSpeed(max, step(angle))
                angle <= 180 -> WheelSpeed(max - step(angle - 90), max)
                angle <= 270 -> WheelSpeed(max - step(angle - 90), -max)
                angle <= 360 -> WheelSpeed(-max, -(max - step(angle - 270)))
                else -> WheelSpeed(0, 0)
            }
        }
    }

    operator fun invoke(angle: Int, power: Float, speedLimit: Int) {
        if (angle < 0 || power <= STICK_DEADZONE) {
            controlRepository.stopWheelNow()
        } else {
            val maxSpeed = (speedLimit * power).roundToInt()
            controlRepository.submitWheel(mixSpeed(angle, maxSpeed))
        }
    }
}
