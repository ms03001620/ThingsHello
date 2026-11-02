package org.mark.carpi.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mark.carpi.model.ControlEvent
import org.mark.carpi.repository.datasource.ControlDataSource
import org.mark.carpi.repository.protocol.CommandParser
import org.mark.carpi.repository.protocol.FrameCodec
import java.io.IOException

/**
 * 控制指令仓库：从 [ControlDataSource] 读字节，解析成 [ControlEvent] 抛给上层。
 * 一次只处理一个连接；无缓冲，上层处理完一条（emit 返回）才读下一帧。干净 EOF 或截断/非法帧都视为断开。
 * 读帧是阻塞 IO，收集方应在 IO dispatcher 上收集。
 */
class CommandRepository(private val source: ControlDataSource) {
    fun events(): Flow<ControlEvent> = flow {
        source.connections().collect { connection ->
            emit(ControlEvent.Connected)
            try {
                connection.use {
                    while (true) {
                        val frame = FrameCodec.read(it.input) ?: break
                        emit(ControlEvent.Received(CommandParser.received(frame)))
                    }
                }
            } catch (e: IOException) {
                System.err.println("client: $e")
            }
            emit(ControlEvent.Disconnected)
        }
    }
}
