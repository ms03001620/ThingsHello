package org.mark.thingshello.ctrl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.lib_unit_socket.bean.JsonReceiver;

public class SimpleJsonReceiver extends JsonReceiver {
    @Override
    public void onReceiverJson(String json, int type) {

    }

    @Override
    public void onExceptionToReOpen(@NonNull Exception e) {

    }

    @Override
    public void onLogMessage(String message, @Nullable Exception e) {

    }

    @Override
    public void onStatusChange(@NonNull Status status) {

    }
}
