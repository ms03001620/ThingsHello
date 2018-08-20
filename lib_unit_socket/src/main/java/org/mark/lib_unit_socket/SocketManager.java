package org.mark.lib_unit_socket;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketManager {
    public interface OnReceiveMessage{
        void onReceiveMessage(byte[] message, int type);
    }
    private static final String TAG = "SocketManager";
    private static volatile SocketManager instance;

    private SocketService mTextService;
    private ExecutorService mExecutorForWrite;

    private SocketManager() {
    }

    public static SocketManager getInstance() {
        if (instance == null) {
            synchronized (SocketManager.class) {
                if (instance == null) {
                    instance = new SocketManager();
                }
            }
        }
        return instance;
    }

    public void init(final OnReceiveMessage listener) {
        mTextService = new SocketService(8010, new ClientMessageCallback() {
            @Override
            public void onReceiveMessage(byte[] bytes, int type) {
                listener.onReceiveMessage(bytes, type);
            }

            @Override
            public void onExceptionToReOpen(@NonNull Exception e) {
                Log.d(TAG, "重新启动", e);
                mTextService.stop();
                mTextService.start();
            }

            @Override
            public void onLogMessage(String message, @Nullable Exception e) {
                Log.d(TAG, message, e);
            }

            @Override
            public void onStatusChange(@NonNull Status status) {

            }
        });
    }

    public void start() {
        mTextService.start();
        mExecutorForWrite = Executors.newCachedThreadPool();
    }


    public void stop() {
        Log.d(TAG, "终止连接", null);
        mTextService.stop();
        mExecutorForWrite.shutdown();
    }


    public void send(@NonNull final String message) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mTextService.writeText(message,(byte)2);
            }
        });
    }

    public void send(@NonNull final byte[] bytes) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mTextService.writeBytes(bytes,(byte)2);
            }
        });
    }


}
