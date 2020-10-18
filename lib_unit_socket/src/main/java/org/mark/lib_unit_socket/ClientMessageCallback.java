package org.mark.lib_unit_socket;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface ClientMessageCallback {
    enum Status {
        NO_CONNECT,
        CONNECTING,
        CONNECTED,
    }


    void onReceiveMessage(byte[] bytes, int type);

    void onExceptionToReOpen(@NonNull Exception e);

    void onLogMessage(String message, @Nullable Exception e);

    void onStatusChange(@NonNull Status status);
}
