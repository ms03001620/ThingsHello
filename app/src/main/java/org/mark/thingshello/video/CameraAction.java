package org.mark.thingshello.video;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import org.mark.base.CommandConstant;
import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.MainActivity;
import org.mark.thingshello.ctrl.OnReceiverCommand;

/**
 * Created by Mark on 2018/8/19
 */
public class CameraAction extends OnReceiverCommand {
    MainActivity.OnCtrlResponse listener;

    public CameraAction(MainActivity.OnCtrlResponse listener) {
        this.listener = listener;
    }


    public void sendWhat(int what) {
        Messenger messenger = listener.getMessenger();
        if (messenger != null) {
            Message message = Message.obtain();
            message.what = what;
            message.replyTo = mMessengerFromCameraService;
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private Messenger mMessengerFromCameraService = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            final byte[] bytes = bundle.getByteArray("text");
            sendFromSocket(bytes);
        }
    });

    private void sendFromSocket(byte[] bytes) {
        //Log.d(CameraService.TAG, "发送数据" + bytes.length / 1024 + "KB");

        if (SocketManager.getInstance().isConnection()) {
            SocketManager.getInstance().send(bytes);
        }
    }

    @Override
    public void onCommand(@NonNull byte[] bytes, int type) {
        int data = decodeByteAsInteger(bytes);
        switch (data) {
            case CommandConstant.CAMERA.START:
                sendWhat(CameraService.CameraServiceAction.CAMERA_OPEN);
                break;
            case CommandConstant.CAMERA.STOP:
                sendWhat(CameraService.CameraServiceAction.CAMERA_CLOSE);
                break;
        }
    }

    @Override
    public void release() {

    }
}
