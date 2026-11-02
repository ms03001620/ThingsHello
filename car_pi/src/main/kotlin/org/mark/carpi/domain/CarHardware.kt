package org.mark.carpi.domain

/**
 * 硬件端口（领域层定义，适配器实现）。每个方法带触发它的指令 id（定时释放 = 触发指令 id，启动蜂鸣/断开/关闭 = 0），
 * 实现方可用于日志；实现失败抛 RuntimeException，由 CarController 记为 rejected。
 */
interface CarHardware : AutoCloseable {
    /** 绑定车轮（真实硬件：配置 PWM 并校验频率）。 */
    fun wheelBind(id: Long)
    fun wheelSpeed(id: Long, left: Int, right: Int)
    fun wheelRelease(id: Long)
    fun servoBind(id: Long)
    fun servoPulseMs(id: Long, pulseMs: Double)
    /** 停止舵机脉冲并把引脚设回输入（mode 0）。 */
    fun servoRelease(id: Long)
    fun light(id: Long, on: Boolean)
    fun buzzer(id: Long, enabled: Boolean)
    fun allStop(id: Long)
    override fun close()
}

/** 非硬件事件日志（command / rejected / unsupported.type，以及适配器内部事件如 pwm.configuredHz）。 */
fun interface ActionLog {
    fun event(id: Long, action: String, value: Any?)
}
