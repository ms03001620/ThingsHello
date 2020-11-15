package org.mark.prework;

import android.content.Context;

import com.david.camerapush.ffmpeg.FFmpegHandler;

import org.mark.camera.DoorbellCamera;

import org.mark.prework.component.ConfigData;
import org.mark.prework.component.FFmpengReader;

import java.lang.ref.WeakReference;

/**
 * Created by mark on 2020/11/3
 */

public class MainPresenter {
    WeakReference<MainActivity> weakReference;

    //ffplay rtmp://106.54.179.144:1935/live/room
    final static String RTMP_TENCENT = "rtmp://106.54.179.144:1935/live/room";


    final static String RTMP_LOCAL = "rtmp://192.168.43.8:1935/live/room";

    public MainPresenter(MainActivity activity) {
        weakReference = new WeakReference<>(activity);
    }

    public void stop() {
        DoorbellCamera.getInstance().shutDown();
        FFmpegHandler.getInstance().close();
    }

    public void start(Context context, ConfigData configData) {
        int ret = FFmpegHandler.getInstance().init(RTMP_TENCENT);
        System.out.println("init success:" + (ret == 0));

        FFmpengReader ffReader = new FFmpengReader(configData.getWidth(), configData.getHeight());
        DoorbellCamera.getInstance().initializeCamera(context, ffReader);
    }

}
