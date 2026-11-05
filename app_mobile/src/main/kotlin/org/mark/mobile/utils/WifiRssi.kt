package org.mark.mobile.utils

import android.content.Context
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager

/**
 * 读取手机自身 Wi-Fi RSSI（dBm）；不可用或无效时返回 null。
 *
 * 注意：Android 10+ 在无定位权限时会脱敏 WifiInfo（SSID 未知、networkId=-1），
 * 但 RSSI 往往仍可用。不能再用 networkId==-1 当作「未连接」。
 */
object WifiRssi {
    @Suppress("DEPRECATION")
    fun read(context: Context): Int? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            if (!wm.isWifiEnabled) return null
            val info = wm.connectionInfo ?: return null
            val state = info.supplicantState
            if (state != null &&
                state != SupplicantState.COMPLETED &&
                state != SupplicantState.ASSOCIATED
            ) {
                return null
            }
            val rssi = info.rssi
            if (rssi <= -127 || rssi >= 0) null else rssi
        } catch (_: SecurityException) {
            null
        }
    }
}
