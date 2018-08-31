package org.mark.mobile.connect.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

/**
 * Created by Mark on 2018/8/31
 */
public interface IReceiver {


    void start();

    void stop();

    void addCallback(ClientMessageCallback callback);

    void removeCallback(ClientMessageCallback callback);


}
