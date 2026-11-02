package org.mark.carpi.runtime

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.mark.carpi.protocol.FrameCodec
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

/** Runs the real TCP server with a recording driver; never outputs hardware signals. */
object ProtocolVerification {
    private fun check(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun run(): Map<String, Any> {
        val log = Files.createTempFile("car-pi-protocol-", ".jsonl")
        try {
            val port: Int
            CarRuntime(CarConfig("127.0.0.1", 0, log)).use { runtime ->
                runtime.start()
                port = runtime.port()
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 3000
                    val out = socket.getOutputStream()
                    for (b in FrameCodec.encode(0, """{"left":35,"right":-20}""")) { out.write(b.toInt()); out.flush() }
                    val batch = ByteArrayOutputStream()
                    batch.write(FrameCodec.encode(1, """{"red":1,"green":0,"blue":0}"""))
                    batch.write(FrameCodec.encode(2, """{"di":true}"""))
                    batch.write(FrameCodec.encode(4, """{"progress":100}"""))
                    out.write(batch.toByteArray()); out.flush()
                    awaitActions(log, "servo.release", "buzzer.enabled")
                    out.write(FrameCodec.encode(6, """{"rotate":"LEFT","speed":40}"""))
                    out.write(FrameCodec.encode(6, """{"rotate":"RIGHT","speed":25}"""))
                    out.write(FrameCodec.encode(0, """{"left":0,"right":0}"""))
                    out.write(FrameCodec.encode(4, """{"progress":101}"""))
                    out.write(FrameCodec.encode(1, "not json"))
                    out.write(FrameCodec.encode(3, "{}"))
                    out.write(FrameCodec.encode(0, """{"left":10,"right":10}"""))
                    out.flush()
                    awaitEvent(log, 11, "wheel.speed")
                }
            }
            val events = events(log)
            expect(events, 1, "wheel.speed", """{"left":35,"right":-20}""")
            expect(events, 2, "light.rgb", "[100,100,100]")
            expect(events, 3, "buzzer.enabled", "true"); expect(events, 3, "buzzer.enabled", "false")
            expect(events, 4, "wheel.release", "true"); expect(events, 4, "servo.pulseMs", "1.5"); expect(events, 4, "servo.release", "true")
            expect(events, 5, "wheel.speed", """{"left":-40,"right":40}""")
            expect(events, 6, "wheel.speed", """{"left":25,"right":-25}""")
            expect(events, 7, "wheel.speed", """{"left":0,"right":0}""")
            check(events.count { it.get("action").asString == "rejected" } == 2, "invalid commands rejected")
            expect(events, 10, "unsupported.type", "3")
            expect(events, 11, "wheel.speed", """{"left":10,"right":10}""")
            ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("127.0.0.1", port)) }
            return mapOf(
                "status" to "PASS", "verification" to "已验证",
                "scope" to "TCP loopback + recording, no physical output or old phone acceptance",
                "checks" to listOf("fragmented header/body", "coalesced frames", "types 0,1,2,4,6", "unsupported type logged",
                    "recorded action values", "servo/wheel exclusion", "500ms releases", "invalid JSON/range rejection",
                    "connection survives rejected commands", "port rebind after shutdown"),
                "events" to events,
            )
        } finally {
            Files.deleteIfExists(log)
        }
    }

    private fun awaitActions(log: Path, a: String, b: String) {
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            val all = events(log)
            if (all.any { it.get("action").asString == a } &&
                all.any { it.get("action").asString == b && it.get("value").toString() == "false" }) return
            Thread.sleep(20)
        }
        throw AssertionError("timed releases missing")
    }

    private fun awaitEvent(log: Path, command: Long, action: String) {
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            if (events(log).any { it.get("command").asLong == command && it.get("action").asString == action }) return
            Thread.sleep(20)
        }
        throw AssertionError("connection did not survive rejected commands")
    }

    private fun events(log: Path): List<JsonObject> =
        Files.readAllLines(log).filter { it.isNotBlank() }.map { JsonParser.parseString(it).asJsonObject }

    private fun expect(events: List<JsonObject>, command: Long, action: String, value: String) {
        check(events.any {
            it.get("command").asLong == command && it.get("action").asString == action &&
                it.get("value") == JsonParser.parseString(value)
        }, "missing $command $action=$value")
    }
}
