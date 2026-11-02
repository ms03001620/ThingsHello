package org.mark.carpi.runtime

import com.google.gson.JsonParser
import org.mark.carpi.repository.protocol.FrameCodec
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

/**
 * doctor 的部署环境自检：用打好的包在本机 JVM 上起真实 TCP 控制服务（模拟硬件，不输出任何硬件信号），
 * 发一帧停车指令并确认被分发、记录，关闭后确认端口可重绑。
 * 证明依赖齐全、协议栈与分发链路可用、TCP 能收发；协议细节由单元测试 ProtocolTest 覆盖。
 */
object SelfCheck {
    fun loopback(): Map<String, Any> = try {
        val log = Files.createTempFile("car-pi-selfcheck-", ".jsonl")
        try {
            val port: Int
            CarRuntime(CarConfig("127.0.0.1", 0, log)).use { runtime ->
                runtime.start()
                port = runtime.port
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 3000
                    socket.getOutputStream().apply {
                        write(FrameCodec.encode(0, """{"left":0,"right":0}"""))
                        flush()
                    }
                    awaitAction(log, "wheel.speed")
                }
            }
            ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("127.0.0.1", port)) }
            mapOf("status" to "PASS", "checks" to listOf("tcp loopback send/receive", "command dispatched and recorded", "port rebind after shutdown"))
        } finally {
            Files.deleteIfExists(log)
        }
    } catch (e: Throwable) {
        mapOf("status" to "FAIL", "reason" to e.toString())
    }

    /** 动作日志可写：父目录不存在则创建，再确认目录可写（不改动已有日志）。 */
    fun actionsWritable(actions: Path): Map<String, Any> = try {
        val dir = actions.toAbsolutePath().parent
        Files.createDirectories(dir)
        val ok = Files.isWritable(dir) && (!Files.exists(actions) || Files.isWritable(actions))
        if (ok) mapOf("status" to "PASS", "path" to actions.toString())
        else mapOf("status" to "FAIL", "path" to actions.toString(), "reason" to "not writable")
    } catch (e: Exception) {
        mapOf("status" to "FAIL", "path" to actions.toString(), "reason" to e.toString())
    }

    private fun awaitAction(log: Path, action: String) {
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            val found = Files.readAllLines(log).filter { it.isNotBlank() }
                .any { JsonParser.parseString(it).asJsonObject.get("action").asString == action }
            if (found) return
            Thread.sleep(20)
        }
        throw AssertionError("$action not recorded within 3s")
    }
}
