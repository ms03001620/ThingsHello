package org.mark.mobile.connect;

/**
 * Created by Mark on 2018/8/10
 */
public interface IConnect {
    void start();
    void stop();
    void sendMessage(String message);
    void release();
}
