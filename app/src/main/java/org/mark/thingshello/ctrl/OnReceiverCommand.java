package org.mark.thingshello.ctrl;

import android.support.annotation.NonNull;

import com.google.gson.Gson;

import org.mark.lib_unit_socket.bean.CmdConstant;

/**
 * Created by Mark on 2018/7/25
 */
public abstract class OnReceiverCommand {
    protected Gson gson;
    public OnReceiverCommand() {
        gson = new Gson();
    }

    public abstract void onCommand(@NonNull String json, @CmdConstant.TYPE int type);

    public abstract void release();
}
