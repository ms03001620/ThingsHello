package org.mark.lib_unit_socket.bean;

import org.mark.lib_unit_socket.ClientMessageCallback;

/**
 * Created by Mark on 2018/11/20
 */
public abstract class JsonReceiver implements ClientMessageCallback {

    public void onReceiveMessage(byte[] message, @CmdConstant.TYPE int type) {
        onReceiverJson(new String(message), type);
    }

    public abstract void onReceiverJson(String json, @CmdConstant.TYPE int type);
}
