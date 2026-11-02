package org.mark.carpi.repository.linux

/** BCM numbering. PWM timing is provided by native hardware/daemon, never JVM sleep. Failures are IOException. */
interface GpioBackend : AutoCloseable {
    fun mode(pin: Int, mode: Int)
    fun write(pin: Int, high: Boolean)
    fun frequency(pin: Int, hz: Int): Int
    fun range(pin: Int, range: Int)
    fun pwm(pin: Int, duty: Int)
    fun servo(pin: Int, microseconds: Int)
    override fun close()
}
