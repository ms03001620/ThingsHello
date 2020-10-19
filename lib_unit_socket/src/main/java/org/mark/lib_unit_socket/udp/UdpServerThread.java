package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UdpServerThread  {
    private UdpConnection mConnection;

    public UdpServerThread(int port, ClientMessageCallback callback) throws Exception {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));

        mConnection = new UdpConnection(null, socket, callback);
    }

    public void write(byte[] bytes, int type) {
        mConnection.write(bytes, type);
    }

    public synchronized void start() {
        mConnection.start();
    }

    public void stop(boolean ignore) {
        mConnection.stop(ignore);
    }

    public boolean hasClientLinked() {
        return mConnection.hasClientLinked();
    }
}



