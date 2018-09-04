package org.mark.lib_unit_socket.udp;

import android.support.annotation.Nullable;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * 发送和接收，主要操作pocket
 */
public class UdpConnection extends Thread {
    public final static int MAX_LENGTH = 1024 * 64;
    private DatagramSocket mSocket;
    private DatagramPacket mPacketReceive;
    @Nullable
    private Host mHostReceiver;
    private ClientMessageCallback mClientMessageCallback;
    private ClientMessageCallback.Status mStatus;

    public UdpConnection(@Nullable Host hostReceiver, DatagramSocket socket, ClientMessageCallback callback) {
        mHostReceiver = hostReceiver;
        mSocket = socket;
        mPacketReceive = new DatagramPacket(new byte[MAX_LENGTH], MAX_LENGTH);
        mClientMessageCallback = callback;
        mStatus = ClientMessageCallback.Status.NO_CONNECT;
    }

    @Override
    public void run() {
        while (mStatus == ClientMessageCallback.Status.CONNECTED) {
            try {
                mSocket.receive(mPacketReceive);

                if (mHostReceiver == null) {
                    try {
                        mHostReceiver = new Host(mPacketReceive.getAddress(), mPacketReceive.getPort());
                    } catch (Exception e) {
                        mClientMessageCallback.onLogMessage("从对方发送的packet包的地址创建host失败", e);
                    }
                }

                byte[] head = new byte[1];
                byte[] body = new byte[mPacketReceive.getLength() - 1];
                System.arraycopy(mPacketReceive.getData(), 0, head, 0, 1);
                System.arraycopy(mPacketReceive.getData(), 1, body, 0, mPacketReceive.getLength() - 1);

                mClientMessageCallback.onReceiveMessage(body, (int) head[0]);

            } catch (IOException e) {
                mClientMessageCallback.onExceptionToReOpen(e);
                mClientMessageCallback.onStatusChange(ClientMessageCallback.Status.NO_CONNECT);
            }
        }
    }

    public void write(byte[] bytes, int type) {
        if (mHostReceiver == null) {
            mClientMessageCallback.onLogMessage("write失败，找不到接受者 host null", null);
            return;
        }

        byte[] result = new byte[bytes.length + 1];

        System.arraycopy(new byte[]{(byte) type}, 0, result, 0, 1);
        System.arraycopy(bytes, 0, result, 1, bytes.length);

        DatagramPacket sendPacket = new DatagramPacket(result, result.length, mHostReceiver.getAddress(), mHostReceiver.getPort());

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

    /**
     * 是否有客户端链接过此服务器
     * udp无链接。所以只有在启动后并且收到对方的packet证明有客户端可用
     * @return
     */
    public boolean hasClientLinked() {
        if (mStatus == ClientMessageCallback.Status.CONNECTED && mHostReceiver != null) {
            return true;
        }

        return false;
    }
}
