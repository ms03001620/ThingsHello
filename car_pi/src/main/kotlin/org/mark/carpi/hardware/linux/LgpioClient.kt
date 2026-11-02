package org.mark.carpi.hardware.linux

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Owns a liblgpio worker. EOF/TERM closes its GPIO handles; no privileged daemon is used. */
class LgpioClient(config: HardwareConfig, environment: Map<String, String> = emptyMap()) : GpioBackend {
    private val replies = ArrayBlockingQueue<String>(2)
    private val script: Path = Files.createTempFile("car-pi-lgpio-", ".py")
    private val worker: Process
    private val input: BufferedWriter
    private val reader: Thread
    private var closed = false

    init {
        val inactive = linkedMapOf<Int, Int>()
        for (motor in listOfNotNull(config.left, config.right))
            for (pin in listOf(motor.in1, motor.in2, motor.pwm)) inactive[pin] = 0
        for (pin in config.lights) inactive[pin] = if (config.lightHigh == true) 0 else 1
        config.buzzer?.let { inactive[it] = if (config.buzzerHigh == true) 0 else 1 }
        worker = try {
            val resource = LgpioClient::class.java.getResourceAsStream("/lgpio_worker.py")
                ?: throw FileNotFoundException("lgpio_worker.py")
            resource.use { Files.copy(it, script, StandardCopyOption.REPLACE_EXISTING) }
            ProcessBuilder("python3", "-u", script.toString(), Gson().toJson(inactive), config.servo?.toString() ?: "none")
                .apply {
                    environment().putAll(environment)
                    redirectError(ProcessBuilder.Redirect.INHERIT)
                }
                .start()
        } catch (e: Exception) {
            Files.deleteIfExists(script)
            throw e
        }
        input = worker.outputWriter(Charsets.UTF_8)
        reader = Thread({
            try {
                worker.inputReader(Charsets.UTF_8).use { output ->
                    while (true) {
                        val line = output.readLine() ?: break
                        if (!replies.offer(line)) break
                    }
                }
            } catch (ignored: IOException) {
            } finally {
                replies.offer("""{"ok":false,"error":"lgpio worker exited"}""")
            }
        }, "lgpio-replies").apply { start() }
        try {
            response()
        } catch (e: IOException) {
            abort()
            throw e
        }
    }

    private fun response(): Int {
        try {
            val line = replies.poll(2, TimeUnit.SECONDS) ?: run {
                abort()
                throw IOException("lgpio worker timeout")
            }
            val reply = JsonParser.parseString(line).asJsonObject
            if (!reply.get("ok").asBoolean) throw IOException("lgpio: " + reply.get("error").asString)
            return reply.get("value").asInt
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        } catch (e: RuntimeException) {
            abort()
            throw IOException("Invalid lgpio response", e)
        }
    }

    @Synchronized
    private fun command(op: String, pin: Int, value: Int): Int {
        if (closed) throw IOException("lgpio worker closed")
        input.write(Gson().toJson(mapOf("op" to op, "pin" to pin, "value" to value)))
        input.newLine()
        input.flush()
        return response()
    }

    override fun mode(pin: Int, mode: Int) { command("mode", pin, mode) }
    override fun write(pin: Int, high: Boolean) { command("write", pin, if (high) 1 else 0) }
    override fun frequency(pin: Int, hz: Int): Int = command("frequency", pin, hz)
    override fun range(pin: Int, range: Int) { command("range", pin, range) }
    override fun pwm(pin: Int, duty: Int) { command("pwm", pin, duty) }
    override fun servo(pin: Int, microseconds: Int) { command("servo", pin, microseconds) }

    val isAlive: Boolean get() = worker.isAlive

    private fun abort() {
        var interrupted = Thread.interrupted()
        try {
            input.close()
            if (!worker.waitFor(500, TimeUnit.MILLISECONDS)) {
                worker.destroy()
                if (!worker.waitFor(500, TimeUnit.MILLISECONDS)) {
                    worker.destroyForcibly()
                    worker.waitFor(500, TimeUnit.MILLISECONDS)
                }
            }
            reader.join(500)
        } catch (ignored: IOException) {
            worker.destroyForcibly()
        } catch (ignored: InterruptedException) {
            worker.destroyForcibly()
            interrupted = true
        } finally {
            closed = true
            try {
                Files.deleteIfExists(script)
            } catch (e: IOException) {
                System.err.println(e)
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        try {
            command("close", 0, 0)
        } finally {
            abort()
        }
    }
}
