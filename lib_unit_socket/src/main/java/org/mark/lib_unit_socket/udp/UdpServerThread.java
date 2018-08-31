package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpServerThread  {
    private UdpConnection mConnection;

    public UdpServerThread(int port, ClientMessageCallback callback) throws Exception {
        DatagramSocket socket = new DatagramSocket(port);

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



