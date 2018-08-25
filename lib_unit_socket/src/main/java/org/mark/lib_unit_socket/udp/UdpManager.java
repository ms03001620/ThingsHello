package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

public class UdpManager {
    private static UdpManager instance;
    private UdpServerThread mUdpServerThread;

    public UdpManager() {
        try {
            mUdpServerThread = new UdpServerThread(8000, new ClientMessageCallback() {
                @Override
                public void onReceiveMessage(byte[] bytes, int type) {
                    System.out.println("onReceiveMessage:" + new String(bytes) + ", type:" + type);
                }

                @Override
                public void onExceptionToReOpen(Exception e) {
                    stop();
                    try {
                        mUdpServerThread = new UdpServerThread(8000, this);
                        start();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }

                @Override
                public void onLogMessage(String message, Exception e) {
                }

                @Override
                public void onStatusChange(Status status) {
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static UdpManager getInstance() {
        if (instance == null) {
            instance = new UdpManager();
        }
        return instance;
    }


    public void start() {
        mUdpServerThread.start();
    }

    public void stop() {
        mUdpServerThread.stop(false);
    }

}



