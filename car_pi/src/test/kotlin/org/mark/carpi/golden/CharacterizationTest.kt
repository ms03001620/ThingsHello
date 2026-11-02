package org.mark.carpi.golden

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.repository.protocol.FrameCodec
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.linux.GpioBackend
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.CarConfig
import org.mark.carpi.runtime.CarRuntime
import java.io.IOException
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * 特征测试（补充 CarViewModelGoldenTest）：真实 TCP 运行时的断开释放 / 单控制端，以及 Linux 硬件 IOException 故障后的 actions.jsonl。
 * 期望值取自重构前实现（bd60aa4 / a310ab9）的实际输出。
 */
class CharacterizationTest {
    private fun stripTime(line: String) = line.replace(Regex("\"time\":\"[^\"]*\","), "")
    private fun lines(log: Path) = Files.readAllLines(log).filter { it.isNotBlank() }.map(::stripTime)
    private fun sim(id: Int, action: String, value: String) = """{"command":$id,"action":"$action","value":$value,"mode":"simulated"}"""
    private fun real(id: Int, action: String, value: String) = """{"command":$id,"action":"$action","value":$value,"mode":"real"}"""
    private fun cmd(type: Int, body: String) = com.google.gson.Gson().toJson(mapOf("type" to type, "body" to body))

    @Test
    fun disconnectReleasesOwnerAndExtraClientIsClosed(@TempDir dir: Path) {
        val log = dir.resolve("actions.jsonl")
        CarRuntime(CarConfig("127.0.0.1", 0, log)).use { runtime ->
            runtime.start()
            Socket("127.0.0.1", runtime.port).use { first ->
                first.getOutputStream().apply { write(FrameCodec.encode(6, "{}")); flush() }
                awaitLine(log, 1, "wheel.speed")
                Socket("127.0.0.1", runtime.port).use { second ->
                    second.soTimeout = 3000
                    assertEquals(-1, second.getInputStream().read()) // 第二个控制端被直接关闭
                }
                first.getOutputStream().apply { write(FrameCodec.encode(4, """{"progress":0}""")); flush() }
                awaitLine(log, 2, "servo.pulseMs")
            }
            awaitLine(log, 0, "servo.release") // 断开：以 id 0 释放 owner
            Thread.sleep(700) // 被取消的舵机定时器不得再以 id 2 释放
        }
        assertEquals(
            listOf(
                sim(1, "command", cmd(6, "{}")), sim(1, "wheel.bind", "true"), sim(1, "wheel.speed", """{"left":-40,"right":40}"""),
                sim(2, "command", cmd(4, """{"progress":0}""")), sim(2, "wheel.release", "true"), sim(2, "servo.bind", "true"),
                sim(2, "servo.pulseMs", "0.8"), sim(0, "servo.release", "true"), sim(0, "all.stop", "true"),
            ),
            lines(log),
        )
    }

    @Test
    fun hardwareFaultRejectsLaterCommands(@TempDir dir: Path) {
        val log = dir.resolve("actions.jsonl")
        val gpio = FailingGpio(failPin = 22)
        val recorder = JsonlActionLogRecorder(log, "real")
        val viewModel = ViewModelDriver(LoggingCarRepository(LinuxCarRepository(gpio, config(), recorder::record), recorder))
        viewModel.send(0, """{"left":10,"right":10}""")
        viewModel.send(4, """{"progress":0}""")
        viewModel.send(1, """{"red":1}""")          // light 写 GPIO 22 失败 -> faulted
        viewModel.send(0, """{"left":1}""")         // 切换 owner 时 servo.release 被拒绝
        viewModel.send(2, """{"di":true}""")
        // 关闭：servo.release / all.stop 都被拒绝，仍按顺序全部尝试，close 以 IllegalStateException 报告，硬件照常关闭
        val error = assertThrows(IllegalStateException::class.java) { viewModel.close() }
        assertEquals("control shutdown", error.message)
        val faulted = "\"java.lang.IllegalStateException: Hardware faulted/closed; restart required\""
        assertEquals(
            listOf(
                real(1, "command", cmd(0, """{"left":10,"right":10}""")), real(1, "wheel.bind", "true"),
                real(1, "pwm.configuredHz", """{"pin":16,"requested":300,"actual":300}"""),
                real(1, "pwm.configuredHz", """{"pin":13,"requested":300,"actual":300}"""),
                real(1, "wheel.speed", """{"left":10,"right":10}"""),
                real(2, "command", cmd(4, """{"progress":0}""")), real(2, "wheel.release", "true"), real(2, "servo.bind", "true"),
                real(2, "servo.pulseMs", "0.8"),
                real(3, "command", cmd(1, """{"red":1}""")), real(3, "light.rgb", "[100,100,100]"),
                real(3, "rejected", "\"java.io.UncheckedIOException: java.io.IOException: gpio 22\""),
                real(4, "command", cmd(0, """{"left":1}""")), real(4, "servo.release", "true"), real(4, "rejected", faulted),
                real(5, "command", cmd(2, """{"di":true}""")), real(5, "buzzer.enabled", "true"), real(5, "rejected", faulted),
                real(0, "servo.release", "true"), real(0, "all.stop", "true"),
            ),
            lines(log),
        )
    }

    private fun config(): HardwareConfig = HardwareConfig.load(Properties().apply {
        Files.newBufferedReader(Path.of("config/pi3-lgpio.properties")).use { load(it) }
    })

    private class FailingGpio(private val failPin: Int) : GpioBackend {
        override fun mode(pin: Int, mode: Int) {}
        override fun write(pin: Int, high: Boolean) { if (pin == failPin) throw IOException("gpio $pin") }
        override fun frequency(pin: Int, hz: Int) = hz
        override fun range(pin: Int, range: Int) {}
        override fun pwm(pin: Int, duty: Int) {}
        override fun servo(pin: Int, microseconds: Int) {}
        override fun close() {}
    }

    private fun awaitLine(log: Path, id: Long, action: String) {
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            if (Files.exists(log) && Files.readAllLines(log).filter { it.isNotBlank() }.map { JsonParser.parseString(it).asJsonObject }
                    .any { it.get("command").asLong == id && it.get("action").asString == action }) return
            Thread.sleep(20)
        }
        throw AssertionError("$id $action not recorded within 3s")
    }
}
