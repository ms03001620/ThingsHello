package org.mark.carpi.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mark.carpi.model.ControlEvent
import org.mark.carpi.repository.protocol.CommandParser
import org.mark.carpi.repository.protocol.FrameCodec
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 控制端数据源：监听 TCP 端口，只接受一个控制端（已有连接时新连接直接关闭），
 * 把帧解析成 [ControlEvent] 抛给上层。上层不接触 socket。
 * 无缓冲（RENDEZVOUS）：上层收下一条后才继续读帧，保持顺序并限制排队。截断/非法帧视为断开。
 */
class CommandRepository(bind: String, port: Int) : AutoCloseable {
    private val server = ServerSocket()
    private val current = AtomicReference<Socket?>(null)
    private val closed = AtomicBoolean()

    init {
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(bind, port))
        } catch (e: Exception) {
            server.close()
            throw e
        }
    }

    val port: Int get() = server.localPort

    fun events(): Flow<ControlEvent> = channelFlow {
        while (isActive && !closed.get()) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                if (!closed.get()) System.err.println("accept: $e")
                continue
            }
            synchronized(current) {
                if (closed.get() || current.get() != null) { socket.close(); null }
                else { current.set(socket); socket }
            }?.let { client ->
                launch {
                    try {
                        client.use {
                            it.tcpNoDelay = true
                            send(ControlEvent.Connected)
                            val input = it.getInputStream()
                            while (true) {
                                val frame = FrameCodec.read(input) ?: break
                                send(ControlEvent.Received(CommandParser.received(frame)))
                            }
                        }
                    } catch (e: IOException) {
                        if (!closed.get()) System.err.println("client: $e")
                    } finally {
                        current.set(null)
                        if (!closed.get()) send(ControlEvent.Disconnected)
                    }
                }
            }
        }
    }.buffer(Channel.RENDEZVOUS).flowOn(Dispatchers.IO)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { server.close() } catch (e: IOException) { System.err.println(e) }
        synchronized(current) {
            try { current.get()?.close() } catch (e: IOException) { System.err.println(e) }
        }
    }
}
