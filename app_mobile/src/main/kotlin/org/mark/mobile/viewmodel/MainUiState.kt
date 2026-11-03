package org.mark.mobile.viewmodel

/**
 * 连接状态枚举
 */
enum class ConnectStatus {
    IDLE,
    CONNECTING,
    ONLINE,
    REFUSED
}

/**
 * 主界面 UI 状态
 */
data class MainUiState(
    val status: ConnectStatus = ConnectStatus.IDLE,
    val isTimeoutError: Boolean = false,
    val hasInputError: Boolean = false,
    val highlightHostError: Boolean = false,
    val connectedPort: String = "8010"
)

/**
 * LiveData 单次事件包装器（用于页面跳转等一次性消费事件）
 */
class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}
