package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClientThread {
    private byte[] receMsgs = new byte[UdpConnection.MAX_LENGTH];

    private Host mHost;
    private UdpConnection mConnection;


    public UdpClientThread(String ip, int port, ClientMessageCallback callback) {
        try {
            mHost = new Host(ip, port);
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(receMsgs, receMsgs.length, mHost.getAddress(), mHost.getPort());
            mConnection = new UdpConnection(socket, packet, callback);

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

    class Host {
        private InetAddress mAddress;
        private int port;

        public Host(String ip, int port) throws Exception {
            mAddress = InetAddress.getByName(ip);
            this.port = port;
        }

        public InetAddress getAddress() {
            return mAddress;
        }

        public int getPort() {
            return port;
        }
    }
}
