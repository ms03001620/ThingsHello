package org.mark.mobile.connect

import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.ClientMessageCallback.Status
import org.mark.lib_unit_socket.ConnectedThread
import org.mark.lib_unit_socket.bean.CmdConstant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 手机端与小车 TCP 控制连接管理单例。
 * 使用单线程调度器保证连接、断开与指令发送严格串行保序。
 */
object ConnectedManager : CmdConstant() {

    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val callbacks = CopyOnWriteArrayList<ClientMessageCallback>()
    private val connectionGen = AtomicLong(0L)
    private var defaultConnect: ConnectedThread? = null

    @Volatile
    var connectedHost: String? = null
        private set

    @Volatile
    var connectedPort: Int = 0
        private set

    @Volatile
    private var lastStatus: Status = Status.NO_CONNECT

    val isConnected: Boolean
        get() = lastStatus == Status.CONNECTED

    fun init(host: String, port: Int) {
        val gen = connectionGen.incrementAndGet()
        connectedHost = host
        connectedPort = port
        writeExecutor.execute {
            defaultConnect?.stop(true)
            defaultConnect = null
            if (gen != connectionGen.get()) return@execute

            defaultConnect = ConnectedThread(host, port, object : ClientMessageCallback {
                override fun onReceiveMessage(bytes: ByteArray?, type: Int) {
                    if (gen != connectionGen.get()) return
                    for (callback in callbacks) {
                        callback.onReceiveMessage(bytes, type)
                    }
                }

                override fun onExceptionToReOpen(e: Exception) {
                    if (gen != connectionGen.get()) return
                    for (callback in callbacks) {
                        callback.onExceptionToReOpen(e)
                    }
                }

                override fun onLogMessage(message: String?, e: Exception?) {
                    if (gen != connectionGen.get()) return
                    for (callback in callbacks) {
                        callback.onLogMessage(message, e)
                    }
                }

                override fun onStatusChange(status: Status) {
                    if (gen != connectionGen.get()) return
                    lastStatus = status
                    for (callback in callbacks) {
                        callback.onStatusChange(status)
                    }
                }
            }).also { thread ->
                if (gen == connectionGen.get()) {
                    thread.start()
                } else {
                    thread.stop(true)
                }
            }
        }
    }

    fun stop() {
        connectionGen.incrementAndGet()
        connectedHost = null
        connectedPort = 0
        val shouldNotify = lastStatus != Status.NO_CONNECT
        lastStatus = Status.NO_CONNECT
        writeExecutor.execute {
            defaultConnect?.stop(true)
            defaultConnect = null
        }
        if (shouldNotify) {
            for (callback in callbacks) {
                callback.onStatusChange(Status.NO_CONNECT)
            }
        }
    }

    fun addCallback(callback: ClientMessageCallback) {
        callbacks.addIfAbsent(callback)
    }

    fun removeCallback(callback: ClientMessageCallback) {
        callbacks.remove(callback)
    }

    public override fun sendMessage(message: String, @CmdConstant.TYPE type: Int) {
        writeExecutor.execute {
            defaultConnect?.write(message, type.toByte())
        }
    }
}
