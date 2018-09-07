package org.mark.base.executor;

import android.support.annotation.NonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 1.异步执行任务
 * 2.同一时间只能执行一个任务
 * 3.如果有任务在运行中没有结束。那么新任务会被放弃
 */
public class BlockProcess {
    private ExecutorService mExecutorForWrite;

    public static BlockProcess getInstance() {
        return ManagerHolder.instance;
    }

    private static class ManagerHolder {
        private static final BlockProcess instance = new BlockProcess();
    }

    private BlockProcess() {
        RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardPolicy();
        mExecutorForWrite = new ThreadPoolExecutor(0, 1, 3000, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(1), handler);
    }

    public void run(@NonNull Runnable runnable) {
        mExecutorForWrite.execute(runnable);
    }
}
