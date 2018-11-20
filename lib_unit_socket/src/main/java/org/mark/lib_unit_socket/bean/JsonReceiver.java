package org.mark.lib_unit_socket.bean;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CmdConstant;

/**
 * Created by Mark on 2018/11/20
 */
public abstract class JsonReceiver implements SocketManager.OnReceiveMessage {
    @Override
    public void onReceiveMessage(byte[] message, @CmdConstant.TYPE int type) {
        onReceiverJson(new String(message), type);
    }

    public abstract void onReceiverJson(String json, @CmdConstant.TYPE int type);
}
