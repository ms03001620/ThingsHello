package org.mark.thingshello.video.sender;

import android.support.annotation.NonNull;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.udp.UdpServerThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Mark on 2018/8/25
 */
public class UdpSender implements ISend {
    private UdpServerThread mUdpServerThread;
    private ExecutorService mExecutorForWrite;

    public UdpSender() {
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
                public void onExceptionToReOpen(@NonNull Exception e) {

                }

                @Override
                public void onLogMessage(String message, Exception e) {
                }

                @Override
                public void onStatusChange(@NonNull Status status) {
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void start() {
        mUdpServerThread.start();
    }

    @Override
    public void send(final byte[] bytes) {
        if (!mUdpServerThread.hasClientLinked()) {
            // udp没有客户端链接过，不知道发送目标是谁
            return;
        }

        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mUdpServerThread.write(bytes, 5);
            }
        });
    }

    public void release() {
        mUdpServerThread.stop(false);
        mExecutorForWrite.shutdown();
    }

}