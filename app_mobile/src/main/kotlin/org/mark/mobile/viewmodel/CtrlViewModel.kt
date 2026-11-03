package org.mark.mobile.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.WheelCmd
import org.mark.lib_unit_socket.bean.WheelRotateCmd
import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.repository.ICarPrefsRepository
import org.mark.mobile.usecase.DriveJoystickUseCase
import org.mark.mobile.usecase.HoldHornUseCase
import org.mark.mobile.usecase.SaveRecentCarUseCase
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * CtrlActivity 对应的 MVVM ViewModel，协同 UseCase 管理连接状态、云台/限速/车灯/鸣笛与遥测展示
 */
class CtrlViewModel(
    private val controlRepository: ICarControlRepository,
    private val prefsRepository: ICarPrefsRepository,
    val isDemo: Boolean = false,
    private val scheduler: UiScheduler = AndroidUiScheduler(),
    private val driveJoystickUseCase: DriveJoystickUseCase = DriveJoystickUseCase(controlRepository),
    private val holdHornUseCase: HoldHornUseCase = HoldHornUseCase(controlRepository, scheduler),
    private val saveRecentCarUseCase: SaveRecentCarUseCase = SaveRecentCarUseCase(
        controlRepository,
        prefsRepository,
        scheduler
    )
) : ViewModel() {

    companion object {
        const val RSSI_WEAK = -75
    }

    val uiState = MutableLiveData(CtrlUiState())
    val returnToConnectRefused = MutableLiveData<Event<Unit>>()

    @Volatile
    private var returning: Boolean = false

    @Volatile
    private var userExited: Boolean = false

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
        val rawSaved = prefsRepository.getSavedSpeed(WheelCmd.DEFAULT_SPEED)
        val initialSpeed = if (rawSaved in WheelCmd.DEFAULT_SPEED..100) rawSaved else WheelCmd.DEFAULT_SPEED
        val (subtitle, connected) = resolveSubtitleAndStatus()

        uiState.value = CtrlUiState(
            subtitleText = subtitle,
            isConnected = connected,
            speedLimit = initialSpeed
        )

        // 先注册监听再检查连接状态，避免注册前发生的断连事件漏捕
        controlRepository.addCallback(clientMessageCallback)

        if (isDemo) {
            applyDemoState()
        }
        checkStillConnected()
    }

    fun setUiState(newState: CtrlUiState) {
        uiState.value = newState
    }

    fun getConnectedHost(): String? = controlRepository.getConnectedHost()

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
        val host = if (isDemo) "192.168.8.190" else controlRepository.getConnectedHost()
        val port = if (isDemo) 8010 else controlRepository.getConnectedPort()
        val connected = isDemo || controlRepository.isConnected()
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
        controlRepository.submitServo(clamped)
    }

    fun onRecenterServo() {
        onServoChanged(50)
    }

    fun onSpeedChanged(value: Int) {
        val clamped = value.coerceIn(0, 100)
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(speedLimit = clamped)
        if (!isDemo) {
            prefsRepository.saveSpeed(clamped)
        }
    }

    fun onJoystickMove(angle: Int, power: Float) {
        val speedLimit = uiState.value?.speedLimit ?: WheelCmd.DEFAULT_SPEED
        driveJoystickUseCase(angle, power, speedLimit)
    }

    fun onRotatePressed(isLeft: Boolean) {
        val speed = uiState.value?.speedLimit ?: WheelCmd.DEFAULT_SPEED
        val dir = if (isLeft) WheelRotateCmd.Rotate.LEFT else WheelRotateCmd.Rotate.RIGHT
        controlRepository.sendWheelRotate(dir, speed)
    }

    fun onRotateReleased(isLeft: Boolean) {
        val dir = if (isLeft) WheelRotateCmd.Rotate.LEFT else WheelRotateCmd.Rotate.RIGHT
        controlRepository.sendWheelRotate(dir, 0)
    }

    fun onToggleLight() {
        val current = uiState.value ?: CtrlUiState()
        val next = !current.isLightOn
        uiState.value = current.copy(isLightOn = next)
        controlRepository.sendLight(next)
    }

    fun syncLightFromPreview(lightOn: Boolean) {
        val current = uiState.value ?: CtrlUiState()
        if (current.isLightOn != lightOn) {
            uiState.value = current.copy(isLightOn = lightOn)
        }
    }

    fun onHornDown() {
        val current = uiState.value ?: CtrlUiState()
        uiState.value = current.copy(isHornPressed = true)
        holdHornUseCase.start()
    }

    fun onHornUp() {
        holdHornUseCase.stop()
        val current = uiState.value ?: CtrlUiState()
        if (current.isHornPressed) {
            uiState.value = current.copy(isHornPressed = false)
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
        saveRecentCarUseCase.scheduleIfNeeded(intentHost, intentPort, isDemo) { returning }
    }

    fun onPause() {
        saveRecentCarUseCase.cancel()
    }

    fun onStop() {
        onHornUp()
        // 离开页面时立即停止车轮运动，取消挂起的云台指令
        controlRepository.cancelServo()
        controlRepository.stopWheelNow()
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
        if (!isDemo && !userExited && !controlRepository.isConnected()) {
            triggerReturnRefused()
        }
    }

    /**
     * 用户主动从遥控页返回连接页（左上角返回键或系统返回）：
     * 立即停轮并主动断开 TCP 连接，释放小车单客户端独占控制权，避免返回首页后锁死槽位或误展示连接被拒。
     */
    fun onActivityFinishing() {
        synchronized(this) {
            if (isDemo || returning || userExited) {
                return
            }
            userExited = true
        }
        saveRecentCarUseCase.cancel()
        onHornUp()
        controlRepository.cancelServo()
        controlRepository.stopWheelNow()
        controlRepository.disconnect()
    }

    private fun triggerReturnRefused() {
        synchronized(this) {
            if (isDemo || returning || userExited) {
                return
            }
            returning = true
        }
        scheduler.runOnUi {
            saveRecentCarUseCase.cancel()
            returnToConnectRefused.value = Event(Unit)
        }
    }

    override fun onCleared() {
        saveRecentCarUseCase.cancel()
        holdHornUseCase.stop()
        scheduler.cancelAll()
        controlRepository.cancelServo()
        controlRepository.removeCallback(clientMessageCallback)
        super.onCleared()
    }

    class Factory(
        private val controlRepository: ICarControlRepository,
        private val prefsRepository: ICarPrefsRepository,
        private val isDemo: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CtrlViewModel::class.java)) {
                return CtrlViewModel(controlRepository, prefsRepository, isDemo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
