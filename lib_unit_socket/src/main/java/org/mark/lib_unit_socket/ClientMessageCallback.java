package org.mark.lib_unit_socket;

import android.support.annotation.NonNull;

public interface ClientMessageCallback {
    enum Status {
        NO_CONNECT,
        CONNECTING,
        CONNECTED,
    }


    void onReceiveMessage(byte[] bytes, int type);

    void onExceptionToReOpen(Exception e);

    void onLogMessage(String message, Exception e);

    void onStatusChange(@NonNull Status status);
}
