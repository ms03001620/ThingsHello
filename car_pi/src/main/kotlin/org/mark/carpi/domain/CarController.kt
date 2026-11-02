package org.mark.carpi.domain

/**
 * 业务规则：WheelAction, CameraServo, ExclusiveBind, ForwardLightAction, BuzzerAction。
 * 单线程使用（无锁）；调用者保证所有方法及 Scheduler 任务串行执行。
 */
class CarController(private val hardware: CarHardware, private val log: ActionLog, private val scheduler: Scheduler) {
    private var servoRelease: Cancellable? = null
    private var buzzerStop: Cancellable? = null
    private var owner = NONE
    private var sequence = 0L

    /** 每帧：先分配 id 并记录原始帧，再执行；任何 RuntimeException 记为 rejected。 */
    fun handle(type: Int, body: String, command: Command) {
        val id = ++sequence
        log.event(id, "command", mapOf("type" to type, "body" to body))
        if (command is Command.Invalid) {
            log.event(id, "rejected", command.reason)
            return
        }
        try {
            execute(id, command)
        } catch (e: RuntimeException) {
            log.event(id, "rejected", e.toString())
        }
    }

    private fun execute(id: Long, command: Command) {
        when (command) {
            is Command.Invalid -> Unit // 已在 handle 中处理
            is Command.Wheel -> {
                activate(id, WHEEL)
                hardware.wheelSpeed(id, command.left, command.right)
            }
            is Command.Light -> {
                hardware.light(id, command.on)
            }
            is Command.Beep -> if (command.enabled) beepNow(id, 500)
            is Command.Servo -> {
                activate(id, SERVO)
                servoRelease?.cancel()
                hardware.servoPulseMs(id, 0.8 + 0.007 * command.progress)
                servoRelease = scheduler.schedule(SERVO_RELEASE_MS) {
                    if (owner == SERVO) {
                        hardware.servoRelease(id)
                        owner = NONE
                    }
                }
            }
            is Command.Rotate -> {
                activate(id, WHEEL)
                val left = command.direction == Command.Direction.LEFT
                val s = command.speed
                hardware.wheelSpeed(id, if (left) -s else s, if (left) s else -s)
            }
            is Command.Unsupported -> log.event(id, "unsupported.type", command.type)
        }
    }

    private fun activate(id: Long, next: String) {
        if (next == owner) return
        servoRelease?.cancel()
        if (owner != NONE) release(id, owner)
        owner = NONE
        if (next == WHEEL) hardware.wheelBind(id) else hardware.servoBind(id)
        owner = next
    }

    fun beep(id: Long, ms: Int) = beepNow(id, ms)

    private fun beepNow(id: Long, ms: Int) {
        buzzerStop?.cancel()
        hardware.buzzer(id, true)
        buzzerStop = scheduler.schedule(ms.toLong()) { hardware.buzzer(id, false) }
    }

    /** 控制端断开：释放运动部件。 */
    fun releaseControl() {
        servoRelease?.cancel()
        try {
            if (owner != NONE) release(0, owner)
        } finally {
            owner = NONE
        }
    }

    /** 关闭：取消定时器 -> 释放 owner -> all.stop（hardware.close 由运行时在停队列后调用）。 */
    fun shutdown() {
        servoRelease?.cancel()
        buzzerStop?.cancel()
        try {
            if (owner != NONE) release(0, owner)
        } finally {
            hardware.allStop(0)
        }
    }

    private fun release(id: Long, device: String) = if (device == WHEEL) hardware.wheelRelease(id) else hardware.servoRelease(id)

    private companion object {
        const val NONE = "none"
        const val WHEEL = "wheel"
        const val SERVO = "servo"
        const val SERVO_RELEASE_MS = 500L
    }
}
