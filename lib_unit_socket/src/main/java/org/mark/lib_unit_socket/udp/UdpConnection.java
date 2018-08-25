package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpConnection extends Thread {
    public final static int MAX_LENGTH = 1024;
    private DatagramSocket mSocket;
    private DatagramPacket mPacket;

    private ClientMessageCallback mClientMessageCallback;
    private ClientMessageCallback.Status mStatus;

    public UdpConnection(DatagramSocket socket, DatagramPacket packet, ClientMessageCallback callback) {
        mSocket = socket;
        mPacket = packet;
        mClientMessageCallback = callback;
        mStatus = ClientMessageCallback.Status.NO_CONNECT;
    }

    @Override
    public void run() {
        while (mStatus == ClientMessageCallback.Status.CONNECTED) {
            try {
                mSocket.receive(mPacket);

                byte[] head = new byte[1];
                byte[] body = new byte[mPacket.getLength() - 1];
                System.arraycopy(mPacket.getData(), 0, head, 0, 1);
                System.arraycopy(mPacket.getData(), 1, body, 0, mPacket.getLength() - 1);

                mClientMessageCallback.onReceiveMessage(body, (int) head[0]);

            } catch (IOException e) {
                mClientMessageCallback.onExceptionToReOpen(e);
                mClientMessageCallback.onStatusChange(ClientMessageCallback.Status.NO_CONNECT);
            }
        }
    }

    public void write(byte[] bytes, int type) {
        byte[] result = new byte[bytes.length + 1];

        System.arraycopy(new byte[]{(byte) type}, 0, result, 0, 1);
        System.arraycopy(bytes, 0, result, 1, bytes.length);

        DatagramPacket sendPacket = new DatagramPacket(result, result.length, mPacket.getAddress(), mPacket.getPort());

        try {
            mSocket.send(sendPacket);
        } catch (IOException e) {
            mClientMessageCallback.onExceptionToReOpen(e);
            mClientMessageCallback.onStatusChange(ClientMessageCallback.Status.NO_CONNECT);
        }
    }

    @Override
    public synchronized void start() {
        mStatus = ClientMessageCallback.Status.CONNECTED;
        mClientMessageCallback.onStatusChange(ClientMessageCallback.Status.CONNECTED);
        super.start();
    }

    public void stop(boolean ignore) {
        mStatus = ClientMessageCallback.Status.NO_CONNECT;
        mClientMessageCallback.onStatusChange(ClientMessageCallback.Status.NO_CONNECT);
        mSocket.close();
    }
}
