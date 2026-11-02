package org.mark.carpi.hardware.logging

import org.mark.carpi.domain.ActionLog
import org.mark.carpi.domain.CarHardware

/**
 * 动作日志装饰器：每个硬件调用先写一行 actions.jsonl 再委托（硬件失败时该行仍在），
 * 动作名/值与旧的字符串协议完全一致。同时作为领域 ActionLog 记录非硬件事件。close：先关硬件，再关 recorder。
 */
class LoggingCarHardware(private val delegate: CarHardware, private val recorder: ActionLogRecorder) : CarHardware, ActionLog {
    override fun event(id: Long, action: String, value: Any?) = recorder.record(id, action, value)

    override fun wheelBind(id: Long) { event(id, "wheel.bind", true); delegate.wheelBind(id) }
    override fun wheelSpeed(id: Long, left: Int, right: Int) {
        event(id, "wheel.speed", mapOf("left" to left, "right" to right)); delegate.wheelSpeed(id, left, right)
    }
    override fun wheelRelease(id: Long) { event(id, "wheel.release", true); delegate.wheelRelease(id) }
    override fun servoBind(id: Long) { event(id, "servo.bind", true); delegate.servoBind(id) }
    override fun servoPulseMs(id: Long, pulseMs: Double) { event(id, "servo.pulseMs", pulseMs); delegate.servoPulseMs(id, pulseMs) }
    override fun servoRelease(id: Long) { event(id, "servo.release", true); delegate.servoRelease(id) }
    override fun light(id: Long, on: Boolean) {
        val level = if (on) 100 else 0
        event(id, "light.rgb", listOf(level, level, level)); delegate.light(id, on)
    }
    override fun buzzer(id: Long, enabled: Boolean) { event(id, "buzzer.enabled", enabled); delegate.buzzer(id, enabled) }
    override fun allStop(id: Long) { event(id, "all.stop", true); delegate.allStop(id) }

    override fun close() {
        try { delegate.close() } finally { recorder.close() }
    }
}
