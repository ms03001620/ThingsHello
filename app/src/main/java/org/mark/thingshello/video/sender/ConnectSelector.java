package org.mark.thingshello.video.sender;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Created by Mark on 2018/8/25
 */
public class ConnectSelector implements ISend {
    private UdpSender mSender;
    private Messenger messenger;

    public ConnectSelector(String target, Messenger messenger) {
        this.messenger = messenger;
        mSender = new UdpSender();
    }

    @Override
    public void send(byte[] bytes) {
        mSender.send(bytes);
    }

    public void release() {
        mSender.release();
    }

    /**
     * TCP 发送
     * @param text message body
     * @param what 0 text; 1 json
     */
    public void sendTextTcp(String text, int what) {
        if (messenger != null) {
            Message response = Message.obtain();
            response.what = what;
            Bundle bundle = new Bundle();
            bundle.putByteArray("text", text.getBytes());
            response.setData(bundle);

            try {
                messenger.send(response);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendTextTcp(String text) {
        sendTextTcp(text, 0);
    }
}



