package org.mark.base;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Mark on 2018/10/25
 */
public class PreferUtils {
    private SharedPreferences mSharedPreferences;

    public void init(Context context) {
        mSharedPreferences = context.getSharedPreferences("client", Context.MODE_PRIVATE);
    }

    private static class PreferHolder {
        private static final PreferUtils instance = new PreferUtils();
    }

    public static PreferUtils getInstance() {
        return PreferHolder.instance;
    }

    public void put(String key, String value){
        mSharedPreferences.edit().putString(key, value).apply();
    }

    public String get(String key, String value){
        return mSharedPreferences.getString(key, value);
    }


    //TODO save object
    public void put(String key, Object o){

    }

    public <T extends Object> T get(String key) {
        mSharedPreferences.getString(key, "");


        return null;
    }
}
