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
            Bundle bundle= msg.getData();
            final byte[] bytes = bundle.getByteArray("image");
            switch (msg.what) {
                case 100:
                    sendFromSocket(bytes);
                    break;
                case 200:
                    showInApp(bytes);
                    break;
                default:
                    break;
            }
        }
    });

    private void sendFromSocket(byte[] bytes){
        //Log.d(CameraService.TAG, "发送数据" + bytes.length / 1024 + "KB");

        if (SocketManager.getInstance().isConnection()) {
            SocketManager.getInstance().send(bytes);
        } else {
            stopPreview();
        }
    }

    private void showInApp(final byte[] bytes){
        listener.getImage().post(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                listener.getImage().setImageBitmap(bitmap);
                // bitmap.recycle();
            }
        });
    }

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
