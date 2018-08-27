package org.mark.lib_unit_socket;

import android.os.Parcelable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 最为socket服务，运行后等待外部连接。对方切断可继续恢复到等待连接状态
 * AcceptThread负责等待连接。连接完毕后交给ConnectedThread负责写入和读取
 * 只接受一个1连接进来
 */
public class SocketService {
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private ClientMessageCallback mReceiveMessageCallback;
    private int mPort;

    public SocketService(int port, ClientMessageCallback receiveMessageCallback) {
        mPort = port;
        this.mReceiveMessageCallback = receiveMessageCallback;
    }

    public synchronized void start() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread(mPort, new OnAccepted() {
                @Override
                public void accept(Socket socket) {
                    if (mConnectedThread != null) {
                        mConnectedThread.cancel();
                        mConnectedThread = null;
                    }

                    if (mAcceptThread != null) {
                        mAcceptThread.cancel();
                        mAcceptThread = null;
                    }

                    mConnectedThread = new ConnectedThread(socket, mReceiveMessageCallback);
                    mConnectedThread.start();
                }
            });
            mAcceptThread.start();
        }
    }

    public synchronized void stop() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
    }

    private class AcceptThread extends Thread {
        private final ServerSocket mServerSocket;
        private OnAccepted mAccepted;

        public AcceptThread(int port, OnAccepted onAccepted) {
            ServerSocket tmp = null;
            try {
                tmp = new ServerSocket(port);
                mAccepted = onAccepted;
            } catch (IOException e) {
                mReceiveMessageCallback.onLogMessage("AcceptThread", e);
            }
            mServerSocket = tmp;
        }

        public void run() {
            setName("AcceptThread:" + mPort);
            while (mServerSocket != null) {
                synchronized (SocketService.this) {
                    try {
                        mReceiveMessageCallback.onLogMessage("等待接入..", null);
                        Socket socket = mServerSocket.accept();
                        mReceiveMessageCallback.onLogMessage("连接到" + socket.toString(), null);
                        mAccepted.accept(socket);
                        break;
                    } catch (IOException e) {
                        mReceiveMessageCallback.onLogMessage("AcceptThread accept", e);
                    }
                }
            }
        }

        public void cancel() {
            try {
                mServerSocket.close();
            } catch (Exception e) {
                mReceiveMessageCallback.onLogMessage("AcceptThread cancel", e);
            }
        }
    }

    public void writeText(String message, byte type) {
        writeBytes(message.getBytes(), type);
    }

    public boolean isConnected() {
        if (mConnectedThread == null) {
            return false;
        }
        return mConnectedThread.isConnected();
    }

    public void writeBytes(byte[] bytes, byte type) {
        if (isConnected()) {
            mConnectedThread.write(bytes, type);
        } else {
            mReceiveMessageCallback.onLogMessage("没有连接无法写入", null);
        }
    }

}
