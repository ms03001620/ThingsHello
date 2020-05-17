package org.mark.lib_unit_socket;

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
    private byte[] headBytes = new byte[5];
    private ClientMessageCallback mReceiveMessageCallback;
    private ClientMessageCallback.Status mStatus;

    public ConnectedThread(Socket socket, ClientMessageCallback callback) {
        setName("t-" + CalendarUtils.getTimeNowHHMMSS());
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
            mReceiveMessageCallback.onLogMessage(getName() + ", 已连接Socket:" + socket.toString(), null);
        } catch (Exception e) {
            updateStatus(ClientMessageCallback.Status.NO_CONNECT);
            mReceiveMessageCallback.onLogMessage(getName() + "开始连接Socket", e);
        }

        mSocket = socket;
        mInputStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
        Exception exception = null;
        long startTime = 0;
        while (isConnected()) {
            try {
                if (!mSocket.isConnected() || mSocket.isInputShutdown()) {
                    throw new Exception(getName() + "Socket没有连接或关闭");
                }

                int headLen = readStillFinish(mInputStream, headBytes);
                if (headLen == -1) {
                    throw new Exception(getName() + getName() + "读取head错误（-1）");
                }

                startTime = System.currentTimeMillis();

                byte[] headInfo = new byte[4];
                headInfo[0] = headBytes[0];
                headInfo[1] = headBytes[1];
                headInfo[2] = headBytes[2];
                headInfo[3] = headBytes[3];

                int length = bytesToInt(headInfo, 0);

                if (length < 1 || length > 500 * 1024) {
                    mReceiveMessageCallback.onLogMessage("解析head错误，放弃数据:" + length, null);
                    continue;
                }

                byte[] bodyBytes = new byte[length];
                int readTotalLen = readStillFinish(mInputStream, bodyBytes);

                try {
                    mReceiveMessageCallback.onReceiveMessage(bodyBytes, (int) headBytes[4]);
                    mReceiveMessageCallback.onLogMessage("读取消息长度" + readTotalLen
                            + ", pass:" + (System.currentTimeMillis() - startTime), null);
                } catch (Exception e) {
                    // 业务层数据异
                    mReceiveMessageCallback.onLogMessage("业务层数据异", e);
                }

            } catch (Exception e) {
                // 连接层异常
                exception = e;
                break;
            }
        }

        if (exception != null) {
            mReceiveMessageCallback.onLogMessage("Socket read错误", exception);
            stop(false);
        }
    }

    private int readStillFinish(InputStream inputStream, byte[] bodyBytes) throws IOException {
        int total = bodyBytes.length;
        int current = inputStream.read(bodyBytes);
        if (current == -1) {
            return -1;
        }
        while (current < total) {
            int unRead = total - current;
            int readLength = inputStream.read(bodyBytes, current, unRead);
            current += readLength;
        }
        return current;
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
            mReceiveMessageCallback.onLogMessage(getName() + "停止连接Socket", null);
        } catch (IOException e) {
            mReceiveMessageCallback.onLogMessage(getName() + "停止连接Socket", e);
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
            byte[] bytes = packageData(data, type);

            mmOutStream.write(bytes);
            mmOutStream.flush();
            mReceiveMessageCallback.onLogMessage("写入数据长度" + data.length, null);
        } catch (Exception e) {
            mReceiveMessageCallback.onLogMessage("写入异常", e);
            updateStatus(ClientMessageCallback.Status.NO_CONNECT);
        }
    }

    public static byte[] packageData(byte[] data, int type){
        // 4 bit for head info
        // 1 bit for types info
        // n bit for data bits
        byte[] result = new byte[4 + 1 + data.length];
        byte[] head = int2byte(data.length);

        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(new byte[]{(byte) type}, 0, result, head.length, 1);
        System.arraycopy(data, 0, result, head.length + 1, data.length);

        return result;
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