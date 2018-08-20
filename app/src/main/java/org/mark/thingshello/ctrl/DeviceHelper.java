package org.mark.thingshello.ctrl;

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

    public void onCommand(byte[] bytes, int type) {
        for (Map.Entry<String, OnReceiverCommand> device : deviceMap.entrySet()) {
            try {
                device.getValue().onCommand(bytes, type);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
        for (Map.Entry<String, OnReceiverCommand> device : deviceMap.entrySet()) {
            device.getValue().release();
        }
        deviceMap.clear();
        deviceMap = null;
    }

    public void didi() {
        Object o = deviceMap.get(BuzzerAction.class.getSimpleName());
        if (o instanceof BuzzerAction) {
            BuzzerAction buzzerAction = (BuzzerAction) o;
            buzzerAction.di();
        }
    }
}
