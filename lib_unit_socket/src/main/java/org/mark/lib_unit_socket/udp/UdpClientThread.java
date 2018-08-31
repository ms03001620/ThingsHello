package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.net.DatagramSocket;

public class UdpClientThread {
    private UdpConnection mConnection;


    public UdpClientThread(String ip, int port, ClientMessageCallback callback) {
        try {
            DatagramSocket socket = new DatagramSocket();
            mConnection = new UdpConnection(new Host(ip, port), socket, callback);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
