package org.mark.thingshello.ctrl;

import android.util.Log;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.comp.bind.ExclusiveBind;
import org.mark.thingshello.ctrl.light.ForwardLightAction;
import org.mark.thingshello.ctrl.servo.CameraServo;
import org.mark.thingshello.ctrl.voice.BuzzerAction;
import org.mark.thingshello.ctrl.wheel.WheelAction;

/**
 * Created by Mark on 2018/7/25
 * adb shell am startservice -n com.google.wifisetup/.WifiSetupService -a WifiSetupService.Connect -e ssid "Xiaomi_5377" -e passphrase "nono12345"
 */
public class DeviceManager {
    private DeviceHelper mDeviceHelper;
    ExclusiveBind exclusiveBind;

    public DeviceManager() throws Exception {
        mDeviceHelper = new DeviceHelper();
        // 该设备可以使用一下硬件
        if ("iot_rpi3".equals(android.os.Build.MODEL)) {
            exclusiveBind = new ExclusiveBind();
            mDeviceHelper.add(new WheelAction(exclusiveBind));
            mDeviceHelper.add(new BuzzerAction());
            mDeviceHelper.add(new ForwardLightAction());
            mDeviceHelper.add(new CameraServo(exclusiveBind));
        }

        SocketManager.getInstance().init(new SimpleJsonReceiver() {
            @Override
            public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
                Log.d("DeviceManager", "onReceiveMessage:" + json.length() + ", type:" + type);
                mDeviceHelper.onCommand(json, type);
            }
        });

        SocketManager.getInstance().start();
        // 告知系统已就绪
        mDeviceHelper.didi();
    }

    public void add(OnReceiverCommand cameraAction) {
        mDeviceHelper.add(cameraAction);
    }

    public void remove(OnReceiverCommand cameraAction) {
        mDeviceHelper.remove(cameraAction);
    }

    public void release() {
        SocketManager.getInstance().stop();
        mDeviceHelper.release();
    }
}
