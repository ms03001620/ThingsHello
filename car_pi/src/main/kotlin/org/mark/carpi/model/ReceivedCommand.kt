package org.mark.carpi.model

/** 收到的一帧：原始 type / body（记录 command 事件用）及解析结果（解析失败为 [Command.Invalid]）。 */
data class ReceivedCommand(val type: Int, val body: String, val command: Command)
