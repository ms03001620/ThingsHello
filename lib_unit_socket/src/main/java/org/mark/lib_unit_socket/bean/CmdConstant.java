package org.mark.lib_unit_socket.bean;

import android.support.annotation.IntDef;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public abstract class CmdConstant {

    public static final int UNDEFINED = -1;
    public static final int WHEEL = 0;
    public static final int BUZZER = 2;
    public static final int LIGHT = 1;
    public static final int CAMERA = 3;
    public static final int CAMERA_SERVO = 4;
    public static final int CAMERA_DEVICE_INFO = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UNDEFINED, WHEEL, BUZZER, LIGHT, CAMERA, CAMERA_SERVO, CAMERA_DEVICE_INFO})
    public @interface TYPE {
    }


    public void sendMessage(String k1, Object v1, String k2, Object v2, @CmdConstant.TYPE final int type) {
        try {
            JSONObject data = new JSONObject();
            data.put(k1, v1);

            if (k2 != null) {
                data.put(k2, v2);
            }

            JSONObject json = new JSONObject();
            json.put("data", data);

            sendMessage(json.toString(), type);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void sendObject(Object value, @CmdConstant.TYPE final int type) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(value);
        sendMessage(jsonString, type);
    }

    protected abstract void  sendMessage(String string, int type);

}
