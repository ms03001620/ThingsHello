package org.mark.mobile.connect.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;

/**
 * Created by Mark on 2018/8/31
 */
public class TcpReceiver implements IReceiver {
    @Override
    public void start() {
        //ConnectedManager.getInstance().sendMessage(CmdConstant.CAMERA.START);
    }

    @Override
    public void stop() {
        //ConnectedManager.getInstance().sendMessage(CmdConstant.CAMERA.STOP);
    }

    @Override
    public void addCallback(ClientMessageCallback callback) {
        ConnectedManager.getInstance().addCallback(callback);
    }

    @Override
    public void removeCallback(ClientMessageCallback callback) {
        ConnectedManager.getInstance().removeCallback(callback);
    }
}
