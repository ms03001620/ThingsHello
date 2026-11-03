package org.mark.mobile.usecase

import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * 持续鸣笛 UseCase：小车每次收到 BuzzerCmd(true) 鸣响 500ms，
 * 按住期间每 [REPEAT_MS] 重发一次，松开或离开页面时立即取消重发。
 */
class HoldHornUseCase(
    private val controlRepository: ICarControlRepository,
    private val scheduler: UiScheduler = AndroidUiScheduler()
) {
    companion object {
        const val REPEAT_MS = 400L
    }

    private var active = false
    private var cancelTick: (() -> Unit)? = null

    fun start() {
        stop()
        active = true
        tick()
    }

    fun stop() {
        active = false
        cancelTick?.invoke()
        cancelTick = null
    }

    private fun tick() {
        if (!active) return
        controlRepository.sendBuzzer(true)
        cancelTick = scheduler.scheduleCancellable(REPEAT_MS) {
            tick()
        }
    }
}
