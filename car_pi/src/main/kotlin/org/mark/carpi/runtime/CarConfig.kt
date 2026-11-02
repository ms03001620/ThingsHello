package org.mark.carpi.runtime

import org.mark.carpi.repository.linux.HardwareConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * car_pi 服务配置（service.properties）。
 */
data class CarConfig(
    val bind: String,
    val port: Int,
    val actions: Path,
    val hardware: HardwareConfig = HardwareConfig.simulated(),
) {
    companion object {
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
