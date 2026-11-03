package org.mark.mobile.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.WheelCmd
import org.mark.lib_unit_socket.bean.WheelRotateCmd
import org.mark.mobile.ctrl.SpeedMixer
import org.mark.mobile.repository.ICtrlRepository
import kotlin.math.roundToInt

/**
 * CtrlActivity 对应的 MVVM ViewModel，负责管理连接监控、最近地址延迟保存、
 * 摇杆差速混控、云台/限速/车灯/鸣笛控制状态及遥测数据展示
 */
class CtrlViewModel(
    private val repository: ICtrlRepository,
    val isDemo: Boolean = false,
    private val scheduler: UiScheduler = AndroidUiScheduler()
) : ViewModel() {

    companion object {
        /** 小车会在约 500ms 后断开被拒绝的第二个控制端；存活超过此时间后再保存最近地址 */
        const val SAVE_RECENT_DELAY_MS = 1000L
        const val HORN_REPEAT_MS = 400L
        const val RSSI_WEAK = -75
        private const val STICK_DEADZONE = 0.02f
    }

    val uiState = MutableLiveData(CtrlUiState())
    val returnToConnectRefused = MutableLiveData<Event<Unit>>()

    private var recentSaved: Boolean = false
    private var cancelSaveRecentTask: (() -> Unit)? = null
    private var cancelHornTask: (() -> Unit)? = null

    @Volatile
    private var returning: Boolean = false

    private val clientMessageCallback = object : ClientMessageCallback {
        override fun onReceiveMessage(message: ByteArray?, type: Int) = Unit

        override fun onExceptionToReOpen(e: Exception) {
            triggerReturnRefused()
        }

        override fun onLogMessage(message: String?, e: Exception?) = Unit

        override fun onStatusChange(status: ClientMessageCallback.Status) {
            scheduler.runOnUi {
                refreshConnectionSubtitle()
            }
            if (status == ClientMessageCallback.Status.NO_CONNECT) {
                triggerReturnRefused()
            }
        }
    }

    init {
        val rawSaved = repository.getSavedSpeed(WheelCmd.DEFAULT_SPEED)
        val initialSpeed = if (rawSaved in 1..100) rawSaved else WheelCmd.DEFAULT_SPEED
        val (subtitle, connected) = resolveSubtitleAndStatus()

        uiState.value = CtrlUiState(
            subtitleText = subtitle,
            isConnected = connected,
            speedLimit = initialSpeed
        )

        // 先注册监听再检查连接状态，避免注册前发生的断连事件漏捕
        repository.addCallback(clientMessageCallback)

        if (isDemo) {
            applyDemoState()
        }
        checkStillConnected()
    }

    fun setUiState(newState: CtrlUiState) {
        uiState.value = newState
    }

    fun getConnectedHost(): String? = repository.getConnectedHost()

    private fun applyDemoState() {
        val (subtitle, connected) = resolveSubtitleAndStatus()
        uiState.value = CtrlUiState(
            subtitleText = subtitle,
            isConnected = connected,
            wifiRssi = -52,
            isWifiWeak = false,
            showNoSignal = true,
            showLiveTag = true,
            bufferMsText = "300",
            speedLimit = 72,
            fpsText = "30",
            bitrateText = "4.2",
            servoPercent = 65,
            isLightOn = true,
            isHornPressed = false
        )
    }

    private fun resolveSubtitleAndStatus(): Pair<String, Boolean> {
        val host = if (isDemo) "192.168.8.190" else repository.getConnectedHost()
        val port = if (isDemo) 8010 else repository.getConnectedPort()
        val connected = isDemo || repository.isConnected()
        val addr = if (host.isNullOrEmpty()) {
            "--"
        } else {
            "$host:${if (port > 0) port else "--"}"
        }
        val text = addr + if (connected) " · 已连接" else " · 未连接"
        return Pair(text, connected)
    }

    private fun refreshConnectionSubtitle() {
        val current = uiState.value ?: CtrlUiState()
        val (subtitle, connected) = resolveSubtitleAndStatus()
        uiState.value = current.copy(
            subtitleText = subtitle,
            isConnected = connected
        )
    }

    fun onServoChanged(value: Int) {
        val clamped = value.coerceIn(0, 100)
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(servoPercent = clamped)
        repository.submitServo(clamped)
    }

    fun onRecenterServo() {
        onServoChanged(50)
    }

    fun onSpeedChanged(value: Int) {
        val clamped = value.coerceIn(0, 100)
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(speedLimit = clamped)
        if (!isDemo) {
            repository.saveSpeed(clamped)
        }
    }

    fun onJoystickMove(angle: Int, power: Float) {
        val current = uiState.value ?: CtrlUiState()
        if (angle < 0 || power <= STICK_DEADZONE) {
            repository.stopWheelNow()
        } else {
            val maxSpeed = (current.speedLimit * power).roundToInt()
            val lr = SpeedMixer.roundSpeed(angle, maxSpeed)
            repository.submitWheel(lr[0], lr[1])
        }
    }

    fun onRotatePressed(isLeft: Boolean) {
        val speed = uiState.value?.speedLimit ?: WheelCmd.DEFAULT_SPEED
        val dir = if (isLeft) WheelRotateCmd.Rotate.LEFT else WheelRotateCmd.Rotate.RIGHT
        repository.sendWheelRotate(dir, speed)
    }

    fun onRotateReleased(isLeft: Boolean) {
        val dir = if (isLeft) WheelRotateCmd.Rotate.LEFT else WheelRotateCmd.Rotate.RIGHT
        repository.sendWheelRotate(dir, 0)
    }

    fun onToggleLight() {
        val current = uiState.value ?: CtrlUiState()
        val next = !current.isLightOn
        uiState.value = current.copy(isLightOn = next)
        repository.sendLight(next)
    }

    fun syncLightFromPreview(lightOn: Boolean) {
        val current = uiState.value ?: CtrlUiState()
        if (current.isLightOn != lightOn) {
            uiState.value = current.copy(isLightOn = lightOn)
        }
    }

    fun onHornDown() {
        cancelHornTask?.invoke()
        cancelHornTask = null
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(isHornPressed = true)
        triggerHornTick()
    }

    /** 小车每次收到 di:true 鸣响 500ms，松开只需停止重发（di:false 为空操作） */
    fun onHornUp() {
        cancelHornTask?.invoke()
        cancelHornTask = null
        val current = uiState.value ?: CtrlUiState()
        if (current.isHornPressed) {
            uiState.value = current.copy(isHornPressed = false)
        }
    }

    private fun triggerHornTick() {
        if (uiState.value?.isHornPressed != true) return
        repository.sendBuzzer(true)
        cancelHornTask = scheduler.scheduleCancellable(HORN_REPEAT_MS) {
            triggerHornTick()
        }
    }

    fun onWifiRssiUpdated(rssi: Int?) {
        if (isDemo) return
        val current = uiState.value ?: CtrlUiState()
        val weak = rssi != null && rssi <= RSSI_WEAK
        uiState.value = current.copy(
            wifiRssi = rssi,
            isWifiWeak = weak
        )
    }

    fun onVideoLiveChanged(live: Boolean) {
        if (isDemo) return
        val current = uiState.value ?: CtrlUiState()
        uiState.value = if (live) {
            current.copy(
                showNoSignal = false,
                showLiveTag = true
            )
        } else {
            current.copy(
                showNoSignal = true,
                showLiveTag = false,
                bufferMsText = null,
                fpsText = null,
                bitrateText = null
            )
        }
    }

    fun onVideoStatsUpdated(bufferMs: String?, fps: String?, bitrateMbps: String?) {
        if (isDemo) return
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(
            bufferMsText = bufferMs,
            fpsText = fps,
            bitrateText = bitrateMbps
        )
    }

    fun onResume(intentHost: String?, intentPort: Int) {
        checkStillConnected()
        if (!returning && !recentSaved) {
            cancelSaveRecentTask?.invoke()
            cancelSaveRecentTask = scheduler.scheduleCancellable(SAVE_RECENT_DELAY_MS) {
                if (returning || isDemo || !repository.isConnected()) return@scheduleCancellable
                if (intentHost.isNullOrEmpty() || intentPort <= 0) return@scheduleCancellable
                repository.saveRecentAddress(intentHost, intentPort)
                recentSaved = true
            }
        }
    }

    fun onPause() {
        cancelSaveRecentTask?.invoke()
        cancelSaveRecentTask = null
    }

    fun onStop() {
        onHornUp()
        // 离开页面时立即停止车轮运动，取消挂起的云台指令
        repository.cancelServo()
        repository.stopWheelNow()
        val current = uiState.value ?: CtrlUiState()
        uiState.value = if (isDemo) {
            current
        } else {
            current.copy(
                showNoSignal = true,
                showLiveTag = false,
                bufferMsText = null,
                fpsText = null,
                bitrateText = null
            )
        }
    }

    fun checkStillConnected() {
        if (!isDemo && !repository.isConnected()) {
            triggerReturnRefused()
        }
    }

    private fun triggerReturnRefused() {
        synchronized(this) {
            if (isDemo || returning) {
                return
            }
            returning = true
        }
        scheduler.runOnUi {
            cancelSaveRecentTask?.invoke()
            cancelSaveRecentTask = null
            returnToConnectRefused.value = Event(Unit)
        }
    }

    override fun onCleared() {
        cancelSaveRecentTask?.invoke()
        cancelHornTask?.invoke()
        scheduler.cancelAll()
        repository.cancelServo()
        repository.removeCallback(clientMessageCallback)
        super.onCleared()
    }

    class Factory(
        private val repository: ICtrlRepository,
        private val isDemo: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CtrlViewModel::class.java)) {
                return CtrlViewModel(repository, isDemo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
