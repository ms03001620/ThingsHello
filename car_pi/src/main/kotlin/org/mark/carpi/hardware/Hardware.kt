package org.mark.carpi.hardware

interface Hardware : AutoCloseable {
    fun action(command: Long, action: String, value: Any?)
    override fun close()
}
