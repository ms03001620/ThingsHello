package org.mark.lib_unit_socket;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by Mark on 2018/7/26
 */
public class ConnectedThread extends Thread {
    private final Socket mSocket;
    private final InputStream mInputStream;
    private final OutputStream mmOutStream;
    private byte[] bytesLength = new byte[4];
    private byte[] bytesType = new byte[1];
    private ClientMessageCallback mReceiveMessageCallback;
    private ClientMessageCallback.Status mStatus;

    public ConnectedThread(Socket socket, ClientMessageCallback callback) {
        mSocket = socket;
        mReceiveMessageCallback = callback;
        updateStatus(ClientMessageCallback.Status.CONNECTING);
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
            updateStatus(ClientMessageCallback.Status.CONNECTED);
        } catch (IOException e) {
            mReceiveMessageCallback.onLogMessage("ConnectedThread createStream", e);
            updateStatus(ClientMessageCallback.Status.NO_CONNECT);
        }

        mInputStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public ConnectedThread(String host, int port, ClientMessageCallback socketCallback) {
        mReceiveMessageCallback = socketCallback;
        mReceiveMessageCallback.onLogMessage("开始连接...", null);
        updateStatus(ClientMessageCallback.Status.CONNECTING);

        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        Socket socket = null;

        try {
            socket = new Socket(host, port);
            // 移除超时设定
            // socket.setSoTimeout(50000);

            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
            updateStatus(ClientMessageCallback.Status.CONNECTED);
            mReceiveMessageCallback.onLogMessage("已连接Socket:" + socket.toString(), null);
        } catch (Exception e) {
            updateStatus(ClientMessageCallback.Status.NO_CONNECT);
            mReceiveMessageCallback.onLogMessage("开始连接Socket", e);
            mReceiveMessageCallback.onExceptionToReOpen(e);
        }

        mSocket = socket;
        mInputStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
        while (isConnected()) {
            try {
                if (!mSocket.isConnected() || mSocket.isInputShutdown()) {
                    mReceiveMessageCallback.onLogMessage("已断开1", null);
                    mReceiveMessageCallback.onExceptionToReOpen(new Exception("已断开1"));
                    break;
                }

                int headLen = mInputStream.read(bytesLength);

                if (headLen < 1) {
                    mReceiveMessageCallback.onLogMessage("已断开2", null);
                    mReceiveMessageCallback.onExceptionToReOpen(new Exception("已断开2"));
                    break;
                }

                int typeLen = mInputStream.read(bytesType);

                if (typeLen < 1) {
                    mReceiveMessageCallback.onLogMessage("已断开3", null);
                    mReceiveMessageCallback.onExceptionToReOpen(new Exception("已断开3"));
                    break;
                }

                int length = bytesToInt(bytesLength, 0) - 1;

                if (length < 1 || length > 500 * 1024) {
                    Log.d("ConnectedThreadError", "放弃数据错误:"+length);
                    continue;
                }

                byte[] bytesData = new byte[length];
                int readMessageLen = mInputStream.read(bytesData);
                mReceiveMessageCallback.onReceiveMessage(bytesData, (int) bytesType[0]);
                mReceiveMessageCallback.onLogMessage("读取消息长度" + readMessageLen, null);

            } catch (Exception e) {
                mReceiveMessageCallback.onLogMessage("读取异常", e);
                mReceiveMessageCallback.onExceptionToReOpen(e);
                stop(false);
                break;
            }
        }

        updateStatus(ClientMessageCallback.Status.NO_CONNECT);
    }

    public boolean isConnected() {
        return mStatus == ClientMessageCallback.Status.CONNECTED;
    }

    public void stop(boolean ignore) {
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            mReceiveMessageCallback.onLogMessage("ConnectedThread stop", e);
        }
        updateStatus(ClientMessageCallback.Status.NO_CONNECT);
    }

    public void write(String message, byte type) {
        byte[] data = message.getBytes();
        write(data, type);
    }

    public void write(byte[] data, byte type) {
        if (!isConnected()) {
            mReceiveMessageCallback.onLogMessage("无法写入没有连接", null);
            return;
        }
        try {
            // 4 bit for head info
            // 1 bit for types info
            // n bit for data bits
            final int finalLength = 4 + 1 + data.length;
            byte[] finalBytes = new byte[finalLength];

            int length = data.length + 1;
            byte[] head = int2byte(length);
            byte[] types = new byte[1];
            types[0] = type;

            System.arraycopy(head, 0, finalBytes, 0, head.length);
            System.arraycopy(types, 0, finalBytes, head.length, 1);
            System.arraycopy(data, 0, finalBytes, head.length + 1, data.length);

            mmOutStream.write(finalBytes);
            mmOutStream.flush();
            mReceiveMessageCallback.onLogMessage("写入数据长度" + length, null);
        } catch (Exception e) {
            mReceiveMessageCallback.onLogMessage("写入异常", e);
            mReceiveMessageCallback.onExceptionToReOpen(e);
            updateStatus(ClientMessageCallback.Status.NO_CONNECT);
        }
    }

    public void cancel() {
        try {
            //mIsRunning = false;
            mSocket.close();
        } catch (IOException e) {
            mReceiveMessageCallback.onLogMessage("ConnectedThread close", e);
        }

        updateStatus(ClientMessageCallback.Status.NO_CONNECT);
    }

    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];
        targets[0] = (byte) (res & 0xff);
        targets[1] = (byte) ((res >> 8) & 0xff);
        targets[2] = (byte) ((res >> 16) & 0xff);
        targets[3] = (byte) (res >>> 24);
        return targets;
    }

    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = ((src[offset] & 0xFF)
                | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    private void updateStatus(ClientMessageCallback.Status status) {
        if (mStatus != status) {
            mStatus = status;
            mReceiveMessageCallback.onStatusChange(mStatus);
        }
    }

}