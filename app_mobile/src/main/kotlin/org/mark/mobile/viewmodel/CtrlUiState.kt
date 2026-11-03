package org.mark.mobile.viewmodel

import org.mark.lib_unit_socket.bean.WheelCmd

/**
 * 遥控界面（CtrlActivity）UI 状态
 */
data class CtrlUiState(
    val subtitleText: String = "-- · 未连接",
    val isConnected: Boolean = false,
    val wifiRssi: Int? = null,
    val isWifiWeak: Boolean = false,
    val showNoSignal: Boolean = true,
    val showLiveTag: Boolean = false,
    val bufferMsText: String? = null,
    val speedLimit: Int = WheelCmd.DEFAULT_SPEED,
    val fpsText: String? = null,
    val bitrateText: String? = null,
    val servoPercent: Int = 50,
    val isLightOn: Boolean = false,
    val isHornPressed: Boolean = false
)
