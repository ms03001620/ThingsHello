package org.mark.thingshello.video.sender;

/**
 * Created by Mark on 2018/8/25
 */
public interface ISend {
    void send(byte[] bytes);
    void release();
}
