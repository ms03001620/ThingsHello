package org.mark.mobile.preview;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Created by Mark on 2018/8/21
 */
public class WorkThreadHandler {

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    public WorkThreadHandler() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Runnable runnable = (Runnable) message.obj;
                runnable.run();
                return true;
            }
        });
    }

    public void runBackground(Runnable runnable) {
        Message message = Message.obtain();
        message.obj = runnable;
        mCameraHandler.sendMessage(message);
    }

    public void release() {
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            mCameraThread = null;
        }
        mCameraHandler = null;
    }

}
