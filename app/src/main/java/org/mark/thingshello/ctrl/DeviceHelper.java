package org.mark.thingshello.ctrl;

import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.voice.BuzzerAction;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Mark on 2018/8/20
 */
public class DeviceHelper {
    private HashMap<String, OnReceiverCommand> deviceMap;

    public DeviceHelper() {
        deviceMap = new HashMap<>();
    }

    public void add(OnReceiverCommand device) {
        deviceMap.put(device.getClass().getSimpleName(), device);
    }

    public void remove(OnReceiverCommand device) {
        OnReceiverCommand dev = deviceMap.get(device.getClass().getSimpleName());
        if (dev != null) {
            dev.release();
            deviceMap.remove(device.getClass().getSimpleName());
        }
    }

    public void onCommand(String json, @CmdConstant.TYPE int type) {
        if (deviceMap.size() == 0) {
            Log.w("DeviceHelper", "onCommand device empty, not accept. type:" + type + " json:" + json);
            return;
        }

        for (Map.Entry<String, OnReceiverCommand> device : deviceMap.entrySet()) {
            try {
                device.getValue().onCommand(json, type);
            } catch (Exception e) {
                Log.e("DeviceHelper", "Error " + device.getKey(), e);
            }
        }
    }

    public void release() {
        for (Map.Entry<String, OnReceiverCommand> device : deviceMap.entrySet()) {
            device.getValue().release();
        }
        deviceMap.clear();
    }

    public void didi() {
        Object o = deviceMap.get(BuzzerAction.class.getSimpleName());
        if (o instanceof BuzzerAction) {
            BuzzerAction buzzerAction = (BuzzerAction) o;
            buzzerAction.di();
        } else {
            Log.e("DeviceHelper", "didi error, not found device");
        }
    }
}
