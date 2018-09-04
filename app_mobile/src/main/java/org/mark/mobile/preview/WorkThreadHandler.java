package org.mark.mobile.preview;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

/**
 * Created by Mark on 2018/8/21
 */
public class WorkThreadHandler {

    private HandlerThread mWorkThread;
    private Handler mHandlerWork;
    private Handler mHandlerUi;

    public WorkThreadHandler() {
        initWorkHandler();
        initUiHandler();
    }

    private void initWorkHandler() {
        mWorkThread = new HandlerThread("CustomWorkThread");
        mWorkThread.start();
        mHandlerWork = new Handler(mWorkThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Runnable runnable = (Runnable) message.obj;
                runnable.run();
                return true;
            }
        });
    }

    private void initUiHandler() {
        mHandlerUi = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Runnable runnable = (Runnable) message.obj;
                runnable.run();
                return true;
            }
        });
    }

    @UiThread
    public void runWorkThread(Runnable runnable) {
        Message message = Message.obtain();
        message.obj = runnable;
        mHandlerWork.sendMessage(message);
    }

    @WorkerThread
    public void runUiThread(Runnable runnable) {
        Message message = Message.obtain();
        message.obj = runnable;
        mHandlerUi.sendMessage(message);
    }

    public void release() {
        if (mWorkThread != null) {
            mWorkThread.quitSafely();
            mWorkThread = null;
        }
        mHandlerWork = null;
        mHandlerUi = null;
    }

}
