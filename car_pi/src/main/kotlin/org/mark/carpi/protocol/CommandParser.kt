package org.mark.carpi.protocol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.mark.carpi.model.Command
import org.mark.carpi.protocol.FrameCodec.Frame

/** 协议适配：Frame(type, JSON body) -> Command。gson 只出现在这里。 */
object CommandParser {
    fun parse(frame: Frame): Command = try {
        decode(frame)
    } catch (e: RuntimeException) {
        Command.Invalid(e.toString())
    }

    private fun decode(frame: Frame): Command {
        // 所有类型（含不支持的）都先要求 body 是 JSON 对象，与原实现一致。
        val o = JsonParser.parseString(frame.body).asJsonObject
        return when (frame.type) {
            0 -> {
                val left = integer(o, "left", 0, -100, 100)
                val right = integer(o, "right", 0, -100, 100)
                Command.Wheel(left, right)
            }
            1 -> {
                // 非短路 or：三个分量都要校验
                val on = (integer(o, "red", 0, 0, 255) > 0) or (integer(o, "green", 0, 0, 255) > 0) or
                    (integer(o, "blue", 0, 0, 255) > 0)
                Command.Light(on)
            }
            2 -> Command.Beep(o.has("di") && o.get("di").asBoolean)
            4 -> Command.Servo(integer(o, "progress", 0, 0, 100))
            6 -> {
                val speed = integer(o, "speed", 40, 0, 100)
                val rotate = if (o.has("rotate")) o.get("rotate").asString else "LEFT"
                require(rotate in setOf("LEFT", "RIGHT")) { "rotate" }
                Command.Rotate(Command.Direction.valueOf(rotate), speed)
            }
            else -> Command.Unsupported(frame.type)
        }
    }

    private fun integer(o: JsonObject, key: String, fallback: Int, min: Int, max: Int): Int {
        if (!o.has(key)) return fallback
        val v = o.get(key)
        require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber) { key }
        val n = v.asBigDecimal.intValueExact()
        require(n in min..max) { "$key out of range" }
        return n
    }
}
