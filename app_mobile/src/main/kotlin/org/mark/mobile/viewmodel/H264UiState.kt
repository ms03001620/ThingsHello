package org.mark.mobile.viewmodel

/**
 * 全屏 RTSP 预览界面（H264Activity）UI 状态
 */
data class H264UiState(
    val isLive: Boolean = false,
    val noSignalText: String = "NO SIGNAL",
    val isHudVisible: Boolean = true,
    val isHintVisible: Boolean = true,
    val isLightOn: Boolean = false,
    val streamPillText: String = "CSI · -- · --FPS · RTSP",
    val bufPillText: String = "BUF --",
    val wifiPillText: String = "手机 WiFi --",
    val ratePillText: String = "--Mbps",
    val servoPillText: String = "云台 50%",
    val speedPillText: String = "限速 --"
)
