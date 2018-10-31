package org.mark.base.thread;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.String.valueOf;

public class MultiThread<T extends List> extends Thread {
    public interface ISubThread<T> {
        void onThreadFinished(Thread thread);
    }


    public interface ICallback<T> {
        void onThreadProcess(T data, String threadName);

        void onAllThreadFinished(long spendMs);
    }

    private T data;
    private ICallback<T> callback;
    private String threadName;
    private ISubThread finishCallback;


    public MultiThread(String name, T data, ICallback<T> callback, ISubThread finishCallback) {
        setName(name);
        threadName = name;
        this.data = data;
        this.callback = callback;
        this.finishCallback = finishCallback;
    }

    @Override
    public void run() {
        callback.onThreadProcess(data, threadName);
        finishCallback.onThreadFinished(this);
    }

    public static <T> void multiThreadProcess(List<T> list, int threadCount, final ICallback<List<T>> callback) {
        final long start = System.currentTimeMillis();
        List<List<T>> listGroup = ListUtils.splitList(list, threadCount);
        final List<Thread> temp = new CopyOnWriteArrayList<>();

        for (int i = 0; i < listGroup.size(); i++) {
            List<T> subList = listGroup.get(i);
            MultiThread<List<T>> thread = new MultiThread<>(valueOf(i), subList, callback, new ISubThread() {
                @Override
                public void onThreadFinished(Thread thread) {
                    temp.remove(thread);

                    if (temp.size() == 0) {
                        long end = System.currentTimeMillis() - start;
                        callback.onAllThreadFinished(end);
                    }
                }
            });
            temp.add(thread);
            thread.start();
        }
    }


}