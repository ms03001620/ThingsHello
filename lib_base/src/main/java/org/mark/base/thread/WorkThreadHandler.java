package org.mark.base.thread;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

/**
 * Created by Mark on 2018/8/21
 */
public class WorkThreadHandler {

    private HandlerThread mWorkThread;
    private Handler mHandlerWork;

    public WorkThreadHandler() {
        initWorkHandler();
    }

    private void initWorkHandler() {
        mWorkThread = new HandlerThread("CustomWorkThread");
        mWorkThread.start();
        mHandlerWork = new Handler(mWorkThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Runnable runnable = message.getCallback();
                runnable.run();
                return true;
            }
        });
    }

    @UiThread
    public void runWorkThread(@NonNull Runnable runnable) {
        runWorkThreadDelay(runnable, 0);
    }

    @UiThread
    public void runWorkThreadDelay(@NonNull Runnable runnable, long ms) {
        if (mHandlerWork != null) {
            mHandlerWork.postDelayed(runnable, ms);
        }
    }

    public void runWorkThreadLoop(@NonNull final Runnable runnable) {
        runWorkThreadTimer(runnable, 0);
    }

    /**
     * 指定时间循环执行
     * @param runnable task
     * @param ms interval
     */
    public void runWorkThreadTimer(@NonNull final Runnable runnable, final long ms) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                runnable.run();
                runWorkThreadDelay(this, ms);
            }
        };
        runWorkThreadDelay(r, ms);
    }

    public void release() {
        mHandlerWork = null;
        if (mWorkThread != null) {
            mWorkThread.quitSafely();
            mWorkThread = null;
        }
    }

    public void removeRunnable(Runnable runnable) {
        if (mHandlerWork != null) {
            mHandlerWork.removeCallbacks(runnable);
        }
    }
}
