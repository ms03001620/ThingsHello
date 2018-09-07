package org.mark.base.executor;

import android.support.annotation.NonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Mark on 2018/8/29
 */
public class ImageProcess {

    private ExecutorService mExecutorForWrite;

    private static ImageProcess instance;

    private ImageProcess(){
        RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardPolicy();
        mExecutorForWrite = new ThreadPoolExecutor(0, 4, 3000, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2), handler);

    }

    public static ImageProcess getInstance() {
        if (instance == null) {
            synchronized (ImageProcess.class) {
                if (instance == null) {
                    instance = new ImageProcess();
                }
            }
        }
        return instance;
    }

    public void run(@NonNull Runnable runnable) {
        mExecutorForWrite.execute(runnable);
    }

}
