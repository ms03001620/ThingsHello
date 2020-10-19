package org.mark.thingshello.video.sender;

import android.util.Log;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.udp.UdpServerThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;

/**
 * Created by Mark on 2018/8/25
 */
public class UdpSender implements ISend {
    private UdpServerThread mUdpServerThread;
    private ExecutorService mExecutorForWrite;

    public UdpSender() throws Exception{
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

        // 启动线程等待udp client链接到服务器
        start();
    }

    public void start() {
        mUdpServerThread.start();
    }

    @Override
    public void send(final byte[] bytes) {
        if (!mUdpServerThread.hasClientLinked()) {
            Log.w("UdpSender", "hasClientLinked false");
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