package org.mark.carpi.hardware.logging

/** Records intended actions; does not execute or verify physical output. */
fun interface ActionLogRecorder : AutoCloseable {
    fun record(command: Long, action: String, value: Any?)
    override fun close() {}
}
