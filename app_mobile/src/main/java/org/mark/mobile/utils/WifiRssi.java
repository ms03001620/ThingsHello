package org.mark.mobile.utils;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.Nullable;

/** Phone's own Wi-Fi RSSI (not car data); null when unavailable. */
public final class WifiRssi {
    private WifiRssi() { }

    @Nullable
    public static Integer read(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return null;
            int rssi = info.getRssi();
            return rssi <= -127 || rssi >= 0 ? null : rssi;
        } catch (SecurityException e) {
            return null;
        }
    }
}
