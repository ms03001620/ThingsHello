package org.mark.lib_unit_socket.udp;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpManager {
    private static UdpManager instance;
    private UdpServerThread mUdpServerThread;
    private ExecutorService mExecutorForWrite;

    public UdpManager() {
        initUdp();
    }

    private void initUdp() {
        try {
            mExecutorForWrite = Executors.newCachedThreadPool();
            mUdpServerThread = new UdpServerThread(8000, new ClientMessageCallback() {
                @Override
                public void onReceiveMessage(byte[] bytes, int type) {

                }

                @Override
                public void onExceptionToReOpen(Exception e) {

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

    public void release() {
        mUdpServerThread.stop(false);
        mExecutorForWrite.shutdown();
    }

    public void write(final byte[] bytes, final int type){
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mUdpServerThread.write(bytes, type);
            }
        });
    }

}



