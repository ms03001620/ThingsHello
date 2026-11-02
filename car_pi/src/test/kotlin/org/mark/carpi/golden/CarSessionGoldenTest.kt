package org.mark.carpi.golden

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.session.CarSession
import org.mark.carpi.runtime.carSession
import org.mark.carpi.recordingRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import org.mark.carpi.protocol.FrameCodec.Frame
import java.nio.file.Files
import java.nio.file.Path

/** 特征测试：锁定 CarSession 现有行为产出的 actions.jsonl（去掉 time 字段）逐行一致。 */
class CarSessionGoldenTest {
    private fun stripTime(line: String) = line.replace(Regex("\"time\":\"[^\"]*\","), "")

    private fun run(dir: Path, script: (CarSession, Path) -> Unit): List<String> {
        val log = dir.resolve("actions.jsonl")
        carSession(SimulatedCarRepository.logged(log)).use { script(it, log) }
        return Files.readAllLines(log).map(::stripTime)
    }

    private fun CarSession.send(type: Int, body: String) = submit(Frame(type, body)) {}.get()

    @Test
    fun framesOfAllTypesAndErrors(@TempDir dir: Path) {
        val lines = run(dir) { d, _ ->
            d.send(0, """{"left":10,"right":-20}""")
            d.send(0, """{}""")
            d.send(0, """{"left":101}""")
            d.send(0, """{"left":1.5}""")
            d.send(0, """{"left":1.0,"right":"x"}""")
            d.send(1, """{"red":1}""")
            d.send(1, """{"red":0,"green":0,"blue":0}""")
            d.send(1, """{"red":5,"green":300}""")
            d.send(1, """{"red":5,"blue":-1}""")
            d.send(2, """{"di":false}""")
            d.send(3, """{}""")
            d.send(5, """{"x":1}""")
            d.send(7, """not json""")
            d.send(3, """[1]""")
            d.send(0, """{"left":""")
            d.send(6, """{}""")
            d.send(6, """{"rotate":"RIGHT","speed":30}""")
            d.send(6, """{"rotate":"UP"}""")
            d.send(6, """{"speed":101}""")
            d.send(4, """{"progress":101}""")
            d.send(4, """{"progress":2.5}""")
            d.send(-1, """{}""")
            d.send(0, """{"left":99999999999}""")
        }
        assertEquals(golden("frames"), lines)
    }

    @Test
    fun timersExclusivityReleaseAndClose(@TempDir dir: Path) {
        val lines = run(dir) { d, log ->
            d.send(4, """{"progress":0}""")      // 1: servo bind + pulse
            awaitLine(log, 1, "servo.release")    // servo.release id=1
            d.send(4, """{"progress":100}""")    // 2
            d.send(0, """{"left":5,"right":5}""") // 3: servo.release(by 3) + wheel.bind; old timer cancelled
            Thread.sleep(700)                     // 负向检查：被取消的舵机定时器不得触发（需真实等待超过 500ms）
            d.send(4, """{"progress":50}""")     // 4: wheel.release + servo.bind
            d.releaseControl().get()              // servo.release id 0
            Thread.sleep(700)                     // 负向检查：no timed release
            d.send(2, """{"di":true}""")         // 5: buzzer on
            awaitLine(log, 5, "buzzer.enabled", "false") // buzzer off id 5
            d.beep(10000).get()                   // id 0 buzzer on
            d.send(6, """{"rotate":"LEFT","speed":0}""") // 6: wheel bind
            d.send(6, """{}""")                  // 7: already wheel, no bind
            d.releaseControl().get()
            d.releaseControl().get()              // no owner: nothing
            d.send(4, """{"progress":10}""")     // 8: servo owner at close
        }
        assertEquals(golden("timers"), lines)
    }

    @Test
    fun closeOrderIncludesHardwareCloseLast() {
        val events = mutableListOf<String>()
        val closing = object : org.mark.carpi.repository.CarRepository by SimulatedCarRepository() {
            override fun close() { synchronized(events) { events += "close" } }
        }
        val hw = recordingRepository(closing) { command, action, value -> synchronized(events) { events += "$command:$action:$value" } }
        val d = carSession(hw)
        d.submit(Frame(0, """{"left":1,"right":1}""")) {}.get()
        d.beep(10000).get()
        d.close()
        assertEquals(
            listOf("1:command:{type=0, body={\"left\":1,\"right\":1}}", "1:wheel.bind:true", "1:wheel.speed:{left=1, right=1}",
                "0:buzzer.enabled:true", "0:wheel.release:true", "0:all.stop:true", "close"),
            events,
        )
    }

    /** 轮询（最多 3s）直到出现指定 command id / action（/ value）的行，替代固定 sleep。 */
    private fun awaitLine(log: Path, id: Long, action: String, value: String? = null) {
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            val found = Files.readAllLines(log).filter { it.isNotBlank() }.map { JsonParser.parseString(it).asJsonObject }.any {
                it.get("command").asLong == id && it.get("action").asString == action && (value == null || it.get("value").toString() == value)
            }
            if (found) return
            Thread.sleep(20)
        }
        throw AssertionError("$id $action $value not recorded within 3s")
    }

    private fun golden(name: String): List<String> =
        javaClass.getResource("/golden/$name.jsonl")!!.readText().lines().filter { it.isNotEmpty() }
}
