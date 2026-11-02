package org.mark.carpi.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mark.carpi.model.Command
import org.mark.carpi.recordingRepository
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.linux.LinuxCarRepositoryTest
import org.mark.carpi.repository.protocol.CommandParser
import org.mark.carpi.repository.protocol.FrameCodec.Frame
import java.util.Properties

/** 灯光：协议 R/G/B -> 整体开关 -> light.rgb 日志与 BCM22/27/24 引脚写入（沿用原 ForwardLightAction 全亮语义）。 */
class SetLightUseCaseTest {
    private fun config(activeHigh: Boolean) = HardwareConfig.load(Properties().apply {
        setProperty("hardware.mode", "real"); setProperty("hardware.confirmed", "true")
        setProperty("light.enabled", "true"); setProperty("light.activeHigh", activeHigh.toString())
    })

    /** 返回 (light.rgb 日志值, 引脚写入)。 */
    private fun run(body: String, activeHigh: Boolean = true): Pair<List<Any?>, List<String>> {
        val gpio = LinuxCarRepositoryTest.Fake()
        val log = mutableListOf<Any?>()
        val repo = recordingRepository(LinuxCarRepository(gpio, config(activeHigh)) { _, _, _ -> }) { _, action, value ->
            if (action == "light.rgb") log.add(value)
        }
        val cmd = CommandParser.parse(Frame(1, body)) as Command.Light
        gpio.calls.clear()
        SetLightUseCase(repo)(1, cmd.on)
        return log to gpio.calls.filter { it.startsWith("write:") }
    }

    private val allOn = listOf(listOf(100, 100, 100)) to listOf("write:22:true", "write:27:true", "write:24:true")
    private val allOff = listOf(listOf(0, 0, 0)) to listOf("write:22:false", "write:27:false", "write:24:false")

    @Test fun zeroTurnsAllOff() = assertEquals(allOff, run("""{"red":0,"green":0,"blue":0}"""))
    @Test fun emptyBodyTurnsAllOff() = assertEquals(allOff, run("{}"))

    @Test
    fun anySingleNonzeroChannelTurnsAllOn() {
        for (body in listOf("""{"red":255}""", """{"green":255}""", """{"blue":255}""", """{"red":1}"""))
            assertEquals(allOn, run(body), body)
    }

    @Test fun lowValueIsNotDimmed() = assertEquals(allOn, run("""{"red":10,"green":0,"blue":0}"""))
    @Test fun fullWhite() = assertEquals(allOn, run("""{"red":255,"green":255,"blue":255}"""))
    @Test fun mixed() = assertEquals(allOn, run("""{"red":0,"green":128,"blue":10}"""))

    @Test
    fun activeLowInvertsPinLevelsButNotLog() {
        assertEquals(listOf(listOf(100, 100, 100)) to listOf("write:22:false", "write:27:false", "write:24:false"), run("""{"red":10}""", activeHigh = false))
        assertEquals(listOf(listOf(0, 0, 0)) to listOf("write:22:true", "write:27:true", "write:24:true"), run("{}", activeHigh = false))
    }

    @Test
    fun outOfRangeRejected() {
        for (body in listOf("""{"red":256}""", """{"blue":-1}""", """{"red":1,"green":300}"""))
            assertEquals(true, CommandParser.parse(Frame(1, body)) is Command.Invalid, body)
    }
}
