package org.mark.carpi.hardware.logging

import com.google.gson.Gson
import java.io.BufferedWriter
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/** Writes the action log for simulated or real hardware mode. */
class JsonlActionLogRecorder(path: Path, private val mode: String) : ActionLogRecorder {
    private val writer: BufferedWriter

    init {
        Files.createDirectories(path.toAbsolutePath().parent)
        writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    @Synchronized
    override fun record(command: Long, action: String, value: Any?) {
        try {
            writer.write(
                Gson().toJson(
                    mapOf("time" to Instant.now().toString(), "command" to command, "action" to action, "value" to value, "mode" to mode)
                )
            )
            writer.newLine()
            writer.flush()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Synchronized
    override fun close() {
        try {
            writer.close()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
}
