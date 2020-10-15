package org.mark.thingshello.ctrl;

import android.util.Log;

import org.mark.thingshello.ctrl.comp.bind.ExclusiveBind;
import org.mark.thingshello.ctrl.light.ForwardLightAction;
import org.mark.thingshello.ctrl.servo.CameraServo;
import org.mark.thingshello.ctrl.voice.BuzzerAction;
import org.mark.thingshello.ctrl.wheel.WheelAction;

import java.io.IOException;

/**
 * Created by Mark on 2018/7/25
 * adb shell am startservice -n com.google.wifisetup/.WifiSetupService -a WifiSetupService.Connect -e ssid "Xiaomi_5377" -e passphrase "nono12345"
 */
public class DeviceManager {
    private final DeviceHelper mDeviceHelper;
    ExclusiveBind exclusiveBind;

    public DeviceManager() {
        mDeviceHelper = new DeviceHelper();
        exclusiveBind = new ExclusiveBind();
        // 该设备可以使用一下硬件
        if ("iot_rpi3".equals(android.os.Build.MODEL)) {
            initWheel();
            initBuzzer();
            initLight();
            initCameraServo();
        }
        // "滴滴"蜂鸣器发声提示驱动已就绪
        mDeviceHelper.didi();
    }

    private void initWheel() {
        try {
            mDeviceHelper.add(new WheelAction(exclusiveBind));
        } catch (Exception e) {
            Log.e("DeviceManager", "initWheel:", e);
        }
    }

    private void initBuzzer() {
        try {
            mDeviceHelper.add(new BuzzerAction());
        } catch (IOException e) {
            Log.e("DeviceManager", "initBuzzer:", e);
        }
    }

    private void initLight() {
        try {
            mDeviceHelper.add(new ForwardLightAction());
        } catch (IOException e) {
            Log.e("DeviceManager", "initLight:", e);
        }
    }

    private void initCameraServo() {
        mDeviceHelper.add(new CameraServo(exclusiveBind));
    }

    public void release() {
        mDeviceHelper.release();
    }

    public void onCommand(String json, int type) {
        mDeviceHelper.onCommand(json, type);
    }
}
