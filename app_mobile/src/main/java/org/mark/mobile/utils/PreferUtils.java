package org.mark.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * app_mobile 专用的 SharedPreferences 统一管理类（单例）。
 */
public final class PreferUtils {
    private static final String PREF_NAME = "client";
    private static final String KEY_HOST = "key-host";
    private static final String KEY_PORT = "key-port";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_SPEED = "config_speed";

    private static volatile PreferUtils sInstance;
    private final SharedPreferences mSharedPreferences;

    private PreferUtils(Context context) {
        mSharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static PreferUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PreferUtils.class) {
                if (sInstance == null) {
                    sInstance = new PreferUtils(context);
                }
            }
        }
        return sInstance;
    }

    public boolean hasCached() {
        return mSharedPreferences.contains(KEY_HOST) && mSharedPreferences.contains(KEY_PORT);
    }

    public int getPortNumber() {
        return mSharedPreferences.getInt(KEY_PORT, 0);
    }

    public String getPort() {
        return String.valueOf(getPortNumber());
    }

    public void save(String host, int port) {
        mSharedPreferences.edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .apply();
    }

    /**
     * 增加新地址，如果已存在则置顶作为最近使用
     */
    public void add(@NonNull String host, int port) {
        String old = mSharedPreferences.getString(KEY_ADDRESS, null);
        if (old != null) {
            String[] elements = old.split("#");
            List<String> oldlist = new ArrayList<>();
            Collections.addAll(oldlist, elements);

            oldlist.remove(host);
            oldlist.add(0, host);

            StringBuilder sb = new StringBuilder();
            for (String element : oldlist) {
                sb.append(element).append("#");
            }
            int lastSplit = sb.lastIndexOf("#");
            if (lastSplit != -1) {
                sb.deleteCharAt(lastSplit);
            }
            mSharedPreferences.edit().putString(KEY_ADDRESS, sb.toString()).apply();
        } else {
            mSharedPreferences.edit().putString(KEY_ADDRESS, host).apply();
        }
    }

    @Nullable
    public String[] getAddress() {
        String data = mSharedPreferences.getString(KEY_ADDRESS, null);
        if (data != null) {
            return data.split("#");
        }
        return null;
    }

    public int getSpeed(int defaultSpeed) {
        return mSharedPreferences.getInt(KEY_SPEED, defaultSpeed);
    }

    public void saveSpeed(int speed) {
        mSharedPreferences.edit().putInt(KEY_SPEED, speed).apply();
    }
}
