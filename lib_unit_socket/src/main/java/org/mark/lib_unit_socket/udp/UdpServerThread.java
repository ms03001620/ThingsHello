package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpServerThread  {
    private byte[] receMsgs = new byte[UdpConnection.MAX_LENGTH];
    private UdpConnection mConnection;

    public UdpServerThread(int port, ClientMessageCallback callback) throws Exception {
        DatagramSocket socket = new DatagramSocket(port);
        DatagramPacket packet = new DatagramPacket(receMsgs, receMsgs.length);

        mConnection = new UdpConnection(socket, packet, callback);
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
}



