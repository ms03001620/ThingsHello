package org.mark.prework;

import android.app.Application;

import org.mark.base.PreferUtils;

/**
 * Created by Mark on 2018/10/25
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        PreferUtils.getInstance().init(this);
    }
}
