package org.mark.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferUtils {
    private SharedPreferences mSharedPreferences;

    private final static String KEY_HOST = "key-host";
    private final static String KEY_PORT = "key-port";


    public PreferUtils(Context context) {
        mSharedPreferences = context.getSharedPreferences("client", Context.MODE_PRIVATE);
    }

    public boolean hasCached() {
        return mSharedPreferences.contains(KEY_HOST) && mSharedPreferences.contains(KEY_PORT);
    }

    public String getHost() {
        return mSharedPreferences.getString(KEY_HOST, "");
    }

    public String getPort() {
        int port = mSharedPreferences.getInt(KEY_PORT, 0);
        return String.valueOf(port);
    }

    public void save(String host, int port) {
        mSharedPreferences.edit().putString(KEY_HOST, host).apply();
        mSharedPreferences.edit().putInt(KEY_PORT, port).apply();
    }
}
