package org.mark.thingshello.video;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.MainActivity;
import org.mark.thingshello.ctrl.OnReceiverCommand;

/**
 * Created by Mark on 2018/8/19
 */
public class CameraAction extends OnReceiverCommand{
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
            message.replyTo = replyMessager;
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private Messenger replyMessager = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    Bundle bundle = msg.getData();
                    final byte[] bytes = bundle.getByteArray("image");
                    Log.d(CameraService.TAG, "收到图像数据" + bytes.length);
                    // SocketManager.getInstance().send(bytes);

                    listener.getImage().post(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                            listener.getImage().setImageBitmap(bitmap);
                            // bitmap.recycle();
                        }
                    });

                default:
                    break;
            }
        }
    });

    @Override
    public void onCommand(@NonNull byte[] bytes, int type) {
        int data = decodeByteAsInteger(bytes);
        switch (data){
            case 12:
                startPreview();
                break;
            case 13:
                stopPreview();
                break;
        }
    }

    @Override
    public void release() {

    }
}
