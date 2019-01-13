package org.mark.thingshello.ctrl;

import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.JsonReceiver;
import org.mark.lib_unit_socket.SocketManager;
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
public class CtrlManager {
    private DeviceHelper mDeviceHelper;

    public CtrlManager(final MainActivity.OnCtrlResponse listener) throws Exception {
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
            public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
                listener.onReceiveMessage(json, type);
                //SocketManager.getInstance().send(bytes);
                Log.d("CtrlManager", "onReceiveMessage:" + json.length() + ", type:" + type);
                mDeviceHelper.onCommand(json, type);
            }
        });

        SocketManager.getInstance().start();
        // 告知系统已就绪
        mDeviceHelper.didi();
    }

    public void release() {
        SocketManager.getInstance().stop();
        mDeviceHelper.release();
    }

}
