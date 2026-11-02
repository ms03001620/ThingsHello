package org.mark.carpi.domain

import org.mark.carpi.hardware.Hardware

/**
 * 业务规则：WheelAction, CameraServo, ExclusiveBind, ForwardLightAction, BuzzerAction。
 * 单线程使用（无锁）；调用者保证所有方法及 Scheduler 任务串行执行。
 */
class CarController(private val hardware: Hardware, private val scheduler: Scheduler) {
    private var servoRelease: Cancellable? = null
    private var buzzerStop: Cancellable? = null
    private var owner = NONE
    private var sequence = 0L

    /** 每帧：先分配 id 并记录原始帧，再执行；任何 RuntimeException 记为 rejected。 */
    fun handle(type: Int, body: String, command: Command) {
        val id = ++sequence
        hardware.action(id, "command", mapOf("type" to type, "body" to body))
        if (command is Command.Invalid) {
            hardware.action(id, "rejected", command.reason)
            return
        }
        try {
            execute(id, command)
        } catch (e: RuntimeException) {
            hardware.action(id, "rejected", e.toString())
        }
    }

    private fun execute(id: Long, command: Command) {
        when (command) {
            is Command.Invalid -> Unit // 已在 handle 中处理
            is Command.Wheel -> {
                activate(id, "wheel")
                hardware.action(id, "wheel.speed", mapOf("left" to command.left, "right" to command.right))
            }
            is Command.Light -> {
                val level = if (command.on) 100 else 0
                hardware.action(id, "light.rgb", listOf(level, level, level))
            }
            is Command.Beep -> if (command.enabled) beepNow(id, 500)
            is Command.Servo -> {
                activate(id, "servo")
                servoRelease?.cancel()
                hardware.action(id, "servo.pulseMs", 0.8 + 0.007 * command.progress)
                servoRelease = scheduler.schedule(SERVO_RELEASE_MS) {
                    if (owner == "servo") {
                        hardware.action(id, "servo.release", true)
                        owner = NONE
                    }
                }
            }
            is Command.Rotate -> {
                activate(id, "wheel")
                val left = command.direction == Command.Direction.LEFT
                val s = command.speed
                hardware.action(id, "wheel.speed", mapOf("left" to if (left) -s else s, "right" to if (left) s else -s))
            }
            is Command.Unsupported -> hardware.action(id, "unsupported.type", command.type)
        }
    }

    private fun activate(id: Long, next: String) {
        if (next == owner) return
        servoRelease?.cancel()
        if (owner != NONE) hardware.action(id, "$owner.release", true)
        owner = NONE
        hardware.action(id, "$next.bind", true)
        owner = next
    }

    fun beep(id: Long, ms: Int) = beepNow(id, ms)

    private fun beepNow(id: Long, ms: Int) {
        buzzerStop?.cancel()
        hardware.action(id, "buzzer.enabled", true)
        buzzerStop = scheduler.schedule(ms.toLong()) { hardware.action(id, "buzzer.enabled", false) }
    }

    /** 控制端断开：释放运动部件。 */
    fun releaseControl() {
        servoRelease?.cancel()
        try {
            if (owner != NONE) hardware.action(0, "$owner.release", true)
        } finally {
            owner = NONE
        }
    }

    /** 关闭：取消定时器 -> 释放 owner -> all.stop（hardware.close 由运行时在停队列后调用）。 */
    fun shutdown() {
        servoRelease?.cancel()
        buzzerStop?.cancel()
        try {
            if (owner != NONE) hardware.action(0, "$owner.release", true)
        } finally {
            hardware.action(0, "all.stop", true)
        }
    }

    private companion object {
        const val NONE = "none"
        const val SERVO_RELEASE_MS = 500L
    }
}
