package org.mark.thingshello.video.sender;

import org.mark.lib_unit_socket.udp.UdpManager;

/**
 * Created by Mark on 2018/8/25
 */
public class UdpSender implements ISend {
    UdpManager mUdpManager;

    public UdpSender(){
        mUdpManager = new UdpManager();
        mUdpManager.start();
    }

    @Override
    public void send(byte[] bytes) {
        mUdpManager.write(bytes, 5);
    }

    @Override
    public void release() {
        mUdpManager.release();
    }
}