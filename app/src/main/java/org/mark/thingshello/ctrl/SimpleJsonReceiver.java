package org.mark.thingshello.ctrl;

import org.mark.lib_unit_socket.bean.JsonReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
