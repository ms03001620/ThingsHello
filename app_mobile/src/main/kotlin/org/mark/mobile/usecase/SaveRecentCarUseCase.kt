package org.mark.mobile.usecase

import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.repository.ICarPrefsRepository
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * 小车会在约 500ms 后断开被拒绝的第二个控制端；
 * 进入遥控页后连接存活超过 [SAVE_DELAY_MS] 且未退出时，才保存最近地址并置顶（单次会话仅保存一次）。
 */
class SaveRecentCarUseCase(
    private val controlRepository: ICarControlRepository,
    private val prefsRepository: ICarPrefsRepository,
    private val scheduler: UiScheduler = AndroidUiScheduler()
) {
    companion object {
        const val SAVE_DELAY_MS = 1000L
    }

    private var savedInSession = false
    private var cancelTask: (() -> Unit)? = null

    fun scheduleIfNeeded(
        host: String?,
        port: Int,
        isDemo: Boolean,
        isReturning: () -> Boolean
    ) {
        if (savedInSession || isReturning()) return
        cancel()
        cancelTask = scheduler.scheduleCancellable(SAVE_DELAY_MS) {
            cancelTask = null
            if (isReturning() || isDemo || !controlRepository.isConnected()) return@scheduleCancellable
            if (host.isNullOrEmpty() || port <= 0) return@scheduleCancellable
            prefsRepository.saveRecentAddress(host, port)
            savedInSession = true
        }
    }

    fun cancel() {
        cancelTask?.invoke()
        cancelTask = null
    }
}
