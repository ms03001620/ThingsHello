package org.mark.carpi.golden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mark.carpi.control.CommandDispatcher
import org.mark.carpi.hardware.Hardware
import org.mark.carpi.hardware.simulated.SimulatedHardware
import org.mark.carpi.protocol.FrameCodec.Frame
import java.nio.file.Files
import java.nio.file.Path

/** 特征测试：锁定 CommandDispatcher 现有行为产出的 actions.jsonl（去掉 time 字段）逐行一致。 */
class DispatcherGoldenTest {
    private fun stripTime(line: String) = line.replace(Regex("\"time\":\"[^\"]*\","), "")

    private fun run(dir: Path, script: (CommandDispatcher) -> Unit): List<String> {
        val log = dir.resolve("actions.jsonl")
        CommandDispatcher(SimulatedHardware(log)).use(script)
        return Files.readAllLines(log).map(::stripTime)
    }

    private fun CommandDispatcher.send(type: Int, body: String) = submit(Frame(type, body)) {}.get()

    @Test
    fun framesOfAllTypesAndErrors(@TempDir dir: Path) {
        val lines = run(dir) { d ->
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
        val lines = run(dir) { d ->
            d.send(4, """{"progress":0}""")      // 1: servo bind + pulse
            Thread.sleep(700)                     // servo.release id=1
            d.send(4, """{"progress":100}""")    // 2
            d.send(0, """{"left":5,"right":5}""") // 3: servo.release(by 3) + wheel.bind; old timer cancelled
            Thread.sleep(700)
            d.send(4, """{"progress":50}""")     // 4: wheel.release + servo.bind
            d.releaseControl().get()              // servo.release id 0
            Thread.sleep(700)                     // no timed release
            d.send(2, """{"di":true}""")         // 5: buzzer on
            Thread.sleep(700)                     // buzzer off id 5
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
        val hw = object : Hardware {
            override fun action(command: Long, action: String, value: Any?) { synchronized(events) { events += "$command:$action:$value" } }
            override fun close() { synchronized(events) { events += "close" } }
        }
        val d = CommandDispatcher(hw)
        d.submit(Frame(0, """{"left":1,"right":1}""")) {}.get()
        d.beep(10000).get()
        d.close()
        Thread.sleep(100)
        assertEquals(
            listOf("1:command:{type=0, body={\"left\":1,\"right\":1}}", "1:wheel.bind:true", "1:wheel.speed:{left=1, right=1}",
                "0:buzzer.enabled:true", "0:wheel.release:true", "0:all.stop:true", "close"),
            events,
        )
    }

    private fun golden(name: String): List<String> =
        javaClass.getResource("/golden/$name.jsonl")!!.readText().lines().filter { it.isNotEmpty() }
}
