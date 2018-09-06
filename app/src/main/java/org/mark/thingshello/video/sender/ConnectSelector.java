package org.mark.thingshello.video.sender;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Created by Mark on 2018/8/25
 */
public class ConnectSelector implements ISend {
    private ISend mSender;
    private Messenger messenger;

    public ConnectSelector(String target, Messenger messenger) {
        this.messenger = messenger;
        if ("tcp".equals(target)) {
            mSender = new TcpSender();
        } else if ("udp".equals(target)) {
            mSender = new UdpSender();
        } else {
            throw new IllegalArgumentException("not support target:" + target);
        }
    }

    @Override
    public void send(byte[] bytes) {
        mSender.send(bytes);
    }

    public void release() {
        mSender.release();
    }
}



