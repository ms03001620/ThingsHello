package org.mark.prework.cam.ffmpegpush;

import android.content.Context;

import org.mark.camera.DoorbellCamera;

import java.lang.ref.WeakReference;

/**
 * Created by mark on 2020/11/3
 */

class FFmpegPushPresenter {
    WeakReference<FFmpengPushActivity> weakReference;

    final static String RTMP_TENCENT = "rtmp://106.54.179.144:1935/live/room";
    final static String RTMP_MAC = "rtmp://192.168.43.8:1935/live/room";

    public FFmpegPushPresenter(FFmpengPushActivity activity) {
        weakReference = new WeakReference<>(activity);
    }

    public void stop() {
        DoorbellCamera.getInstance().shutDown();

      //  FFmpegHandler.getInstance().close();
    }


    public void start(Context applicationContext, int w, int h) {
            /*    int ret = FFmpegHandler.getInstance().init(RTMP_TENCENT);
        System.out.println("init ret::"+ret);*/


        ConfigFFReader ffReader = new ConfigFFReader(w, h);
        DoorbellCamera.getInstance().initializeCamera(applicationContext, ffReader);
    }
}
