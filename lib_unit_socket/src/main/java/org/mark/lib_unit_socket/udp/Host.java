package org.mark.lib_unit_socket.udp;

import java.net.InetAddress;

/**
 * Created by Mark on 2018/8/31
 */
public class Host {
    private InetAddress mAddress;
    private int port;

    public Host(String ip, int port) throws Exception {
        this(InetAddress.getByName(ip), port);
    }

    public Host(InetAddress address, int port) throws Exception {
        mAddress = address;
        this.port = port;
    }

    public InetAddress getAddress() {
        return mAddress;
    }

    public int getPort() {
        return port;
    }
}