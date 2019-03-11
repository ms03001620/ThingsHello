package org.mark.thingshello;

import android.app.Application;
import android.content.Context;

/**
 * Created by mark on 2019/3/9
 */
public class App extends Application {

    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }


    public static Context getContext() {
        return mContext;
    }
}
