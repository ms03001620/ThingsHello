package org.mark.prework.cam.preview;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行次数的统计，默认每秒的次数，单线程
 * count 次数，ps 毫秒数
 * 例如 10/1000， 每秒10次
 */
public class SecondsCountUtils {
    private AtomicInteger count;
    private volatile long start;

    private int c;
    private long p;

    public interface OnSecondReport {
        void report(int count, long ms);
    }

    public SecondsCountUtils(){
        count = new AtomicInteger();
    }

    public void run(OnSecondReport report) {
        if (start == 0) {
            start = System.currentTimeMillis();
        } else {
            long now = System.currentTimeMillis();
            long ps = now - start;
            if (ps >= 1000) {
                c = count.getAndSet(0);
                p = ps;
                if (report != null) {
                    report.report(c, p);
                }
                start= now;
            }

        }
        count.incrementAndGet();
    }

    public int getCount(){
        return c;
    }

    public long getPass(){
        return p;
    }

}
