package org.mark.thingshello.video.sender;

import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;

/**
 * Created by Mark on 2018/9/6
 */
public class TcpSender implements ISend {

    @Override
    public void send(byte[] bytes) {
/*        if (messenger != null) {
            Message response = Message.obtain();
            response.what = 100;
            Bundle bundle = new Bundle();
            bundle.putByteArray("image", bytes);
            response.setData(bundle);

            try {
                messenger.send(response);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }*/
    }

    @Override
    public void release() {

    }
}