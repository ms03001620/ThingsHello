package org.mark.carpi

import com.google.gson.GsonBuilder
import org.mark.carpi.runtime.CarConfig
import org.mark.carpi.runtime.CarRuntime
import org.mark.carpi.runtime.ProtocolVerification
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

object CarMain {
    @JvmStatic
    @Throws(Exception::class)
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) {
            "doctor | serve [--config path] | verify --suite protocol [--output path]"
        }
        val options = mutableMapOf<String, String>()
        for (i in 1 until args.size step 2) {
            require(i + 1 < args.size && args[i] in setOf("--config", "--output", "--suite")) {
                "Invalid option: ${args[i]}"
            }
            options[args[i]] = args[i + 1]
        }
        when (args[0]) {
            "doctor" -> {
                val config = CarConfig.load(options["--config"]?.let { Path.of(it) })
                val result = environment()
                result["hardwareMode"] = config.hardware.mode.key
                result["physicalIOConfigured"] = config.hardware.real
                result["physicalIOVerified"] = false
                report(options, result)
            }

            "serve" -> {
                val config = CarConfig.load(options["--config"]?.let { Path.of(it) })
                val runtime = CarRuntime(config)
                val stopped = CountDownLatch(1)
                Runtime.getRuntime().addShutdownHook(Thread({
                    try {
                        runtime.close()
                    } finally {
                        stopped.countDown()
                    }
                }, "car-shutdown"))
                runtime.start()
                println("READY tcp=${runtime.port()} hardware=${config.hardware.mode.key}")
                runtime.startupBeep()
                stopped.await()
            }

            "verify" -> {
                val suite = options["--suite"] ?: ""
                val result = environment()
                result["suite"] = suite
                when (suite) {
                    "protocol" -> {
                        try {
                            result.putAll(ProtocolVerification.run())
                        } catch (e: Exception) {
                            result["status"] = "FAIL"
                            result["reason"] = e.toString()
                        } catch (e: AssertionError) {
                            result["status"] = "FAIL"
                            result["reason"] = e.toString()
                        }
                    }


                    else -> {
                        result["status"] = "BLOCKED"
                        result["reason"] = "Suite not implemented; real hardware unverified"
                    }
                }
                report(options, result)
                if (result["status"] != "PASS") exitProcess(2)
            }

            else -> throw IllegalArgumentException("Unknown command: ${args[0]}")
        }
    }

    @JvmStatic
    fun environment(): MutableMap<String, Any?> = linkedMapOf(
        "time" to Instant.now().toString(),
        "os" to System.getProperty("os.name"),
        "kernel" to System.getProperty("os.version"),
        "arch" to System.getProperty("os.arch"),
        "java" to System.getProperty("java.runtime.version"),
        "version" to (CarMain::class.java.`package`.implementationVersion ?: "development"),
        "physicalIOVerified" to false,
        "tensorflow" to false
    )

    private fun report(options: Map<String, String>, result: Map<String, Any?>) {
        val json = GsonBuilder().setPrettyPrinting().create().toJson(result)
        options["--output"]?.let {
            val path = Path.of(it)
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.writeString(path, "$json\n")
        }
        println(json)
    }
}
