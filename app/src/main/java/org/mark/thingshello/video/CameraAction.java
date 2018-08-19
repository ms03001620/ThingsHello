package org.mark.thingshello.video;

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.mark.thingshello.MainActivity;

/**
 * Created by Mark on 2018/8/19
 */
public class CameraAction {
    MainActivity.OnCtrlResponse listener;

    public CameraAction(MainActivity.OnCtrlResponse listener) {
        this.listener = listener;
    }

    public void startPreview() {
        sendWhat(1);
    }

    public void stopPreview() {
        sendWhat(2);
    }

    private void sendWhat(int what) {
        Messenger messenger = listener.getMessenger();
        if (messenger != null) {
            Message message = Message.obtain();
            message.what = what;
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
