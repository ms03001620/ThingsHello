package org.mark.thingshello.ctrl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;

import org.mark.camera.CameraUtils;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.JsonReceiver;
import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.App;
import org.mark.thingshello.MainActivity;
import org.mark.thingshello.ctrl.light.ForwardLightAction;
import org.mark.thingshello.ctrl.servo.CameraServo;
import org.mark.thingshello.ctrl.voice.BuzzerAction;
import org.mark.thingshello.ctrl.wheel.WheelAction;
import org.mark.thingshello.video.CameraAction;

/**
 * Created by Mark on 2018/7/25
 * adb shell am startservice -n com.google.wifisetup/.WifiSetupService -a WifiSetupService.Connect -e ssid "Xiaomi_5377" -e passphrase "nono12345"
 */
public class DeviceManager {
    private DeviceHelper mDeviceHelper;

    public DeviceManager(final MainActivity.OnCtrlResponse listener) throws Exception {
        mDeviceHelper = new DeviceHelper();
        String model = android.os.Build.MODEL;
        // 该设备可以使用一下硬件
        if ("iot_rpi3".equals(model)) {
            mDeviceHelper.add(new WheelAction());
            mDeviceHelper.add(new BuzzerAction());
            mDeviceHelper.add(new ForwardLightAction());
            mDeviceHelper.add(new CameraServo());
        }
        mDeviceHelper.add(new CameraAction(listener));


        SocketManager.getInstance().init(new JsonReceiver() {

            @Override
            public void onExceptionToReOpen(@NonNull Exception e) {
            }

            @Override
            public void onLogMessage(String message, @Nullable Exception e) {
            }

            @Override
            public void onStatusChange(@NonNull Status status) {

            }

            @Override
            public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
                listener.onReceiveMessage(json, type);

                if(type == CmdConstant.CAMERA){
                    sendDeviceInfo();
                }



                //SocketManager.getInstance().send(bytes);
                Log.d("DeviceManager", "onReceiveMessage:" + json.length() + ", type:" + type);
                mDeviceHelper.onCommand(json, type);
            }
        });

        SocketManager.getInstance().start();
        // 告知系统已就绪
        mDeviceHelper.didi();
    }

    private void sendDeviceInfo() {
        Log.d("DeviceManager", "sendDeviceInfo");
        try {
            CameraUtils.CameraInfo info = CameraUtils.makeCameraInfo(App.getContext());

            Gson gson = new Gson();
            String jsonString = gson.toJson(info);

            SocketManager.getInstance().sendMessage(jsonString, CmdConstant.CAMERA_DEVICE_INFO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void release() {
        SocketManager.getInstance().stop();
        mDeviceHelper.release();
    }

}
