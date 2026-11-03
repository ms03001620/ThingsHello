package org.mark.mobile.utils

import android.content.Context
import android.net.wifi.WifiManager

/**
 * 读取手机自身 Wi-Fi RSSI（dBm）；不可用或无效时返回 null。
 */
object WifiRssi {
    @Suppress("DEPRECATION")
    fun read(context: Context): Int? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            if (!wm.isWifiEnabled) return null
            val info = wm.connectionInfo ?: return null
            if (info.networkId == -1) return null
            val rssi = info.rssi
            if (rssi <= -127 || rssi >= 0) null else rssi
        } catch (_: SecurityException) {
            null
        }
    }
}
