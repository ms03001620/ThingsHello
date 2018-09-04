package org.mark.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * @deprecated {@link PreferUtils#getAddress}
     */
    public String getHost() {
        return mSharedPreferences.getString(KEY_HOST, "");
    }

    public int getPortNumber() {
        return mSharedPreferences.getInt(KEY_PORT, 0);
    }

    public String getPort() {
        int port = getPortNumber();
        return String.valueOf(port);
    }

    public void save(String host, int port) {
        mSharedPreferences.edit().putString(KEY_HOST, host).apply();
        mSharedPreferences.edit().putInt(KEY_PORT, port).apply();
    }

    /**
     * 增加新数据，如果已存在那么将其放置到list首位
     */
    public void add(@NonNull String host, int port) {
        String old = mSharedPreferences.getString("address", null);

        if (old != null) {
            String[] elements = old.split("#");

            List<String> oldlist = new ArrayList<>();
            Collections.addAll(oldlist, elements);


            if (oldlist.contains(host)) {
                oldlist.remove(host);
            }

            // 放在顶部 作为最近使用
            oldlist.add(0, host);

            StringBuilder sb = new StringBuilder();
            for (String element : oldlist) {
                sb.append(element);
                sb.append("#");
            }

            int lastSplit = sb.lastIndexOf("#");

            if (lastSplit != -1) {
                sb.deleteCharAt(lastSplit);
            }

            String newData = sb.toString();

            mSharedPreferences.edit().putString("address", newData).apply();

        } else {
            mSharedPreferences.edit().putString("address", host).apply();
        }
    }

    @Nullable
    public String[] getAddress() {
        String data = mSharedPreferences.getString("address", null);
        if (data != null) {
            return data.split("#");
        }

        return null;
    }
}
