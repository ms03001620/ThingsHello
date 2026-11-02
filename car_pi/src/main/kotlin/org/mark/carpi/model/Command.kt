package org.mark.carpi.model

/** 手机指令的领域表示；由协议适配层从帧解析得到，不依赖任何 JSON 库。 */
sealed class Command {
    data class Wheel(val left: Int, val right: Int) : Command()
    data class Light(val on: Boolean) : Command()
    /** di=true 才鸣叫；di 缺省/false 时为空操作（保持原行为：不产生动作）。 */
    data class Beep(val enabled: Boolean) : Command()
    data class Servo(val progress: Int) : Command()
    data class Rotate(val direction: Direction, val speed: Int) : Command()
    data class Unsupported(val type: Int) : Command()
    /** 解析/校验失败；reason 为原异常的 toString()，原样写入 rejected。 */
    data class Invalid(val reason: String) : Command()

    enum class Direction { LEFT, RIGHT }
}
