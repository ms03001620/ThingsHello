package org.mark.thingshello.video;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CameraCmd;
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

    private Messenger mMessengerFromCameraService = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            final byte[] bytes = bundle.getByteArray("text");
            sendFromSocket(bytes);
        }
    });

    /**
     * 发送相机消息
     * @param bytes 消息
     */
    private void sendFromSocket(byte[] bytes) {
        if (SocketManager.getInstance().isConnection()) {
            SocketManager.getInstance().send(bytes);
        }
    }

    @Override
    public void onCommand(@NonNull String json, @CmdConstant.TYPE int type) {
        if (type == CmdConstant.CAMERA) {
            sendWhat(json);
        }
    }


    public void sendWhat(String json) {
        Log.d(CameraService.TAG, "sendWhat:" + json);
        Messenger messenger = listener.getMessenger();
        if (messenger != null) {
            Bundle bundle = new Bundle();
            bundle.putString("json", json);

            Message message = Message.obtain();
            message.what = 0;
            message.setData(bundle);
            message.replyTo = mMessengerFromCameraService;
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void release() {

    }
}
