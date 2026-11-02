package org.mark.carpi.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mark.carpi.model.ReceivedCommand
import org.mark.carpi.repository.protocol.CommandParser
import org.mark.carpi.repository.protocol.FrameCodec
import java.net.Socket

/**
 * 控制端连接的数据源：把 socket 上的帧解析为 [ReceivedCommand] 冷流。
 * 不加 buffer / flowOn：下游处理完一条（emit 返回）才读下一帧，保持顺序与背压。干净 EOF 结束流，截断/非法帧抛 IOException。
 */
class CommandRepository(private val socket: Socket) {
    fun commands(): Flow<ReceivedCommand> = flow {
        val input = socket.getInputStream()
        while (true) {
            val frame = FrameCodec.read(input) ?: break
            emit(CommandParser.received(frame))
        }
    }
}
