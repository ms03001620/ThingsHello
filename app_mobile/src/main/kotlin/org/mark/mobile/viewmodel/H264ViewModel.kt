package org.mark.mobile.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.usecase.CalculateCatchUpSpeedUseCase
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler
import java.util.Locale

/**
 * H264Activity 对应的 MVVM ViewModel，负责管理 2077 HUD 显隐与自动隐藏、
 * 车灯同步控制、TCP 链路断连监控、低延迟倍速追帧策略计算及遥测胶囊文案更新
 */
class H264ViewModel(
    private val controlRepository: ICarControlRepository,
    val isDemo: Boolean = false,
    private val debugState: String? = null,
    initialLightOn: Boolean = false,
    servoPercent: Int = 50,
    speedLimit: Int = -1,
    private val catchUpSpeedUseCase: CalculateCatchUpSpeedUseCase = CalculateCatchUpSpeedUseCase(),
    private val scheduler: UiScheduler = AndroidUiScheduler()
) : ViewModel() {

    companion object {
        const val HUD_AUTO_HIDE_MS = 4000L
        const val HINT_HIDE_DELAY_MS = 3000L
        const val LINK_WATCH_POLL_MS = 500L
    }

    val uiState = MutableLiveData(H264UiState())
    val finishPreview = MutableLiveData<Event<Boolean>>()

    private var cancelAutoHideTask: (() -> Unit)? = null
    private var cancelHideHintTask: (() -> Unit)? = null
    private var cancelLinkWatchTask: (() -> Unit)? = null

    @Volatile
    private var finishing: Boolean = false

    private val clientMessageCallback = object : ClientMessageCallback {
        override fun onReceiveMessage(message: ByteArray?, type: Int) = Unit

        override fun onExceptionToReOpen(e: Exception) {
            if (!isDemo) {
                requestFinish()
            }
        }

        override fun onLogMessage(message: String?, e: Exception?) = Unit

        override fun onStatusChange(status: ClientMessageCallback.Status) {
            if (!isDemo && status == ClientMessageCallback.Status.NO_CONNECT) {
                requestFinish()
            }
        }
    }

    init {
        val initialState = if (isDemo) {
            H264UiState(
                isLive = true,
                isHudVisible = debugState != "demo_hidden",
                isHintVisible = true,
                isLightOn = initialLightOn,
                streamPillText = "CSI · 1280×720 · 30FPS · RTSP",
                bufPillText = "BUF 300ms",
                wifiPillText = "手机 WiFi -52dBm",
                ratePillText = "4.2Mbps",
                servoPillText = "云台 65%",
                speedPillText = "限速 72%"
            )
        } else {
            H264UiState(
                isLive = false,
                isHudVisible = true,
                isHintVisible = true,
                isLightOn = initialLightOn,
                servoPillText = String.format(Locale.US, "云台 %d%%", servoPercent),
                speedPillText = if (speedLimit < 0) {
                    "限速 --"
                } else {
                    String.format(Locale.US, "限速 %d%%", speedLimit)
                }
            )
        }
        uiState.value = initialState

        controlRepository.addCallback(clientMessageCallback)

        cancelHideHintTask = scheduler.scheduleCancellable(HINT_HIDE_DELAY_MS) {
            val current = uiState.value ?: H264UiState()
            if (current.isHintVisible) {
                uiState.value = current.copy(isHintVisible = false)
            }
        }

        if (!isDemo) {
            scheduleAutoHide()
        }
    }

    fun setUiState(newState: H264UiState) {
        uiState.value = newState
    }

    fun computeCatchUpSpeed(
        isReady: Boolean,
        bufferedMs: Long,
        currentAppliedSpeed: Float
    ): Float = catchUpSpeedUseCase(isReady, bufferedMs, currentAppliedSpeed)

    fun onToggleLight() {
        val current = uiState.value ?: H264UiState()
        val next = !current.isLightOn
        uiState.value = current.copy(isLightOn = next)
        if (!isDemo) {
            controlRepository.sendLight(next)
        }
        bumpHud()
    }

    fun bumpHud() {
        val current = uiState.value ?: H264UiState()
        if (!current.isHudVisible) {
            uiState.value = current.copy(isHudVisible = true)
        }
        scheduleAutoHide()
    }

    fun setHudVisible(show: Boolean) {
        val current = uiState.value ?: H264UiState()
        if (current.isHudVisible != show) {
            uiState.value = current.copy(isHudVisible = show)
        }
        cancelAutoHideTask?.invoke()
        cancelAutoHideTask = null
        if (show && !isDemo) {
            scheduleAutoHide()
        }
    }

    /**
     * 处理双击屏幕：若播放器处于报错状态则重置提示并返回 true 触发重连；同时切换 HUD 显隐
     */
    fun onScreenDoubleTap(hasPlayerError: Boolean): Boolean {
        val current = uiState.value ?: H264UiState()
        val shouldRetry = !isDemo && hasPlayerError
        if (shouldRetry) {
            uiState.value = current.copy(noSignalText = "NO SIGNAL")
        }
        setHudVisible(!current.isHudVisible)
        return shouldRetry
    }

    fun onVideoLiveChanged(live: Boolean) {
        if (isDemo) return
        val current = uiState.value ?: H264UiState()
        if (current.isLive != live) {
            uiState.value = current.copy(isLive = live)
        }
    }

    fun onPlayerError() {
        if (isDemo) return
        val current = uiState.value ?: H264UiState()
        uiState.value = current.copy(
            isLive = false,
            noSignalText = "NO SIGNAL · 双击重试"
        )
    }

    fun onHudStatsUpdated(
        rssi: Int?,
        hasPlayer: Boolean,
        isReady: Boolean,
        bufferedMs: Long,
        videoWidth: Int,
        videoHeight: Int,
        fps: String?,
        rateMbps: String?
    ) {
        if (isDemo) return
        val current = uiState.value ?: H264UiState()
        val wifiText = if (rssi == null) "手机 WiFi --" else "手机 WiFi ${rssi}dBm"
        val bufText = if (hasPlayer && isReady) "BUF ${bufferedMs}ms" else "BUF --"
        val streamText = if (hasPlayer) {
            val res = if (videoWidth > 0) "${videoWidth}×${videoHeight}" else "--"
            "CSI · $res · ${fps ?: "--"}FPS · RTSP"
        } else {
            "CSI · -- · --FPS · RTSP"
        }
        val rateText = "${rateMbps ?: "--"}Mbps"

        uiState.value = current.copy(
            wifiPillText = wifiText,
            bufPillText = bufText,
            streamPillText = streamText,
            ratePillText = rateText
        )
    }

    fun onStart() {
        cancelLinkWatchTask?.invoke()
        cancelLinkWatchTask = null
        scheduleLinkWatchTick()
    }

    fun onStop() {
        cancelLinkWatchTask?.invoke()
        cancelLinkWatchTask = null
    }

    fun onBackRequested() {
        requestFinish()
    }

    private fun scheduleAutoHide() {
        cancelAutoHideTask?.invoke()
        cancelAutoHideTask = null
        if (!isDemo) {
            cancelAutoHideTask = scheduler.scheduleCancellable(HUD_AUTO_HIDE_MS) {
                setHudVisible(false)
            }
        }
    }

    private fun scheduleLinkWatchTick() {
        if (!isDemo && !controlRepository.isConnected()) {
            requestFinish()
            return
        }
        cancelLinkWatchTask = scheduler.scheduleCancellable(LINK_WATCH_POLL_MS) {
            scheduleLinkWatchTick()
        }
    }

    private fun requestFinish() {
        synchronized(this) {
            if (finishing) return
            finishing = true
        }
        scheduler.runOnUi {
            cancelAutoHideTask?.invoke()
            cancelAutoHideTask = null
            cancelLinkWatchTask?.invoke()
            cancelLinkWatchTask = null
            val lightOn = uiState.value?.isLightOn ?: false
            finishPreview.value = Event(lightOn)
        }
    }

    override fun onCleared() {
        cancelAutoHideTask?.invoke()
        cancelHideHintTask?.invoke()
        cancelLinkWatchTask?.invoke()
        scheduler.cancelAll()
        controlRepository.removeCallback(clientMessageCallback)
        super.onCleared()
    }

    class Factory(
        private val controlRepository: ICarControlRepository,
        private val isDemo: Boolean,
        private val debugState: String?,
        private val initialLightOn: Boolean,
        private val servoPercent: Int,
        private val speedLimit: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(H264ViewModel::class.java)) {
                return H264ViewModel(
                    controlRepository = controlRepository,
                    isDemo = isDemo,
                    debugState = debugState,
                    initialLightOn = initialLightOn,
                    servoPercent = servoPercent,
                    speedLimit = speedLimit
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
