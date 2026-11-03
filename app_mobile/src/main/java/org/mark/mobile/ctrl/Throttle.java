package org.mark.mobile.ctrl;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Sends the latest value at most once per interval; a trailing value is flushed once.
 * Identical consecutive values are never re-sent.
 */
public final class Throttle<T> {
    public interface Sink<T> { void send(T value); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long intervalMs;
    private final Sink<T> sink;
    private T lastSent;
    private T pending;
    private long lastTime;
    private final Runnable flush = new Runnable() {
        @Override public void run() {
            if (pending != null) {
                T v = pending;
                pending = null;
                emit(v);
            }
        }
    };

    public Throttle(long intervalMs, Sink<T> sink) {
        this.intervalMs = intervalMs;
        this.sink = sink;
    }

    public void submit(T value) {
        if (value == null || value.equals(lastSent) && pending == null) return;
        long now = SystemClock.uptimeMillis();
        long wait = lastTime + intervalMs - now;
        if (wait <= 0) {
            handler.removeCallbacks(flush);
            pending = null;
            emit(value);
        } else {
            pending = value;
            handler.removeCallbacks(flush);
            handler.postDelayed(flush, wait);
        }
    }

    /** Send immediately (e.g. stop on release), dropping any pending value. */
    public void sendNow(T value) {
        handler.removeCallbacks(flush);
        pending = null;
        emit(value);
    }

    public void cancel() {
        handler.removeCallbacks(flush);
        pending = null;
    }

    private void emit(T v) {
        if (v.equals(lastSent)) return;
        lastSent = v;
        lastTime = SystemClock.uptimeMillis();
        sink.send(v);
    }
}
