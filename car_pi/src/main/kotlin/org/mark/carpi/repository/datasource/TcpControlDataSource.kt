package org.mark.carpi.repository.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** TCP 数据源：监听端口，只接受一个控制端；已有连接时新连接直接关闭。 */
class TcpControlDataSource(bind: String, port: Int) : ControlDataSource {
    private val server = ServerSocket()
    private val lock = Any()
    private var current: Socket? = null
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

    override val port: Int get() = server.localPort

    override fun connections(): Flow<ControlConnection> = channelFlow {
        // accept 一直在跑，这样控制端连着时也能立刻拒绝多余的连接。
        launch {
            while (isActive && !closed.get()) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    if (!closed.get()) System.err.println("accept: $e")
                    continue
                }
                val accepted = synchronized(lock) {
                    if (closed.get() || current != null) false else { current = socket; true }
                }
                if (!accepted) { socket.close(); continue }
                socket.tcpNoDelay = true
                send(Connection(socket))
            }
        }
    }.buffer(Channel.RENDEZVOUS).flowOn(Dispatchers.IO)

    private inner class Connection(private val socket: Socket) : ControlConnection {
        override val input: InputStream = socket.getInputStream()
        override fun close() {
            try { socket.close() } finally { synchronized(lock) { if (current === socket) current = null } }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { server.close() } catch (e: IOException) { System.err.println(e) }
        synchronized(lock) {
            try { current?.close() } catch (e: IOException) { System.err.println(e) }
        }
    }
}
