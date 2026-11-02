package org.mark.carpi.model

/** CommandRepository 向上抛的控制端事件：连上、收到一帧命令、断开。 */
sealed interface ControlEvent {
    data object Connected : ControlEvent
    data class Received(val command: ReceivedCommand) : ControlEvent
    data object Disconnected : ControlEvent
}
