package org.mark.carpi.runtime

import org.mark.carpi.hardware.linux.HardwareConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * car_pi 服务配置（service.properties）。
 * 属性用 @get:JvmName 保留原 Java record 的访问器名（config.port() 等），Java 调用方无需改动。
 */
data class CarConfig @JvmOverloads constructor(
    @get:JvmName("bind") val bind: String,
    @get:JvmName("port") val port: Int,
    @get:JvmName("actions") val actions: Path,
    @get:JvmName("hardware") val hardware: HardwareConfig = HardwareConfig.simulated(),
) {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun load(path: Path?): CarConfig {
            val p = Properties()
            if (path != null) Files.newBufferedReader(path).use { p.load(it) }
            val hardware = HardwareConfig.load(p)
            val port = port(p, "tcp.port", 8010)
            return CarConfig(
                bind = p.getProperty("bind", "0.0.0.0"),
                port = port,
                actions = Path.of(p.getProperty("actions.path", "results/actions.jsonl")),
                hardware = hardware,
            )
        }

        private fun port(p: Properties, key: String, fallback: Int): Int {
            val n = p.getProperty(key, fallback.toString()).toInt()
            require(n in 0..65535) { key }
            return n
        }
    }
}
