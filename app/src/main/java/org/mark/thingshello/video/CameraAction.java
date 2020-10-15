package org.mark.thingshello.video;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;

import org.mark.camera.CameraUtils;
import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.App;
import org.mark.thingshello.ctrl.OnReceiverCommand;

/**
 * Created by Mark on 2018/8/19
 */
public class CameraAction extends OnReceiverCommand {

    private Messenger mMessenger;
    public CameraAction(Messenger messenger) {
        this.mMessenger = messenger;
    }

    final private Messenger mMessengerFromCameraService = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            final byte[] bytes = bundle.getByteArray("text");

            switch (msg.what) {
                case 0:
                    SocketManager.getInstance().sendMessage(bytes, CmdConstant.UNDEFINED);
                    break;
            }
        }
    });


    @Override
    public void onCommand(@NonNull String json, @CmdConstant.TYPE int type) {
        if (type == CmdConstant.CAMERA) {
            sendConfigToCameraService(json);
            sendCameraInfoToClient();
        }
    }


    public void sendConfigToCameraService(String json) {
        Log.d(CameraService.TAG, "sendConfigToCameraService:" + json + ", msg:" + mMessenger);
        if (mMessenger == null) {
            return;
        }
        Messenger messenger = mMessenger;
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

    private void sendCameraInfoToClient() {
        Log.d(CameraService.TAG, "sendCameraInfoToClient");
        try {
            CameraUtils.CameraInfo info = CameraUtils.makeCameraInfo(App.getContext());

            Gson gson = new Gson();
            String jsonString = gson.toJson(info);

            SocketManager.getInstance().sendMessage(jsonString, CmdConstant.CAMERA_DEVICE_INFO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void release() {
        mMessenger = null;
    }
}
