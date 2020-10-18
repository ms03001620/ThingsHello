package org.mark.lib_unit_socket;

import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SocketManager extends CmdConstant {
    private static final String TAG = "SocketManager";
    private static volatile SocketManager instance;

    private SocketService mTextService;
    private ExecutorService mExecutorForWrite;

    private SocketManager() {
        mExecutorForWrite = Executors.newCachedThreadPool();
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

    public void init(final ClientMessageCallback listener) {
        mTextService = new SocketService(8010, new ClientMessageCallback() {
            @Override
            public void onReceiveMessage(byte[] bytes, int type) {
                listener.onReceiveMessage(bytes, type);
            }

            @Override
            public void onExceptionToReOpen(@NonNull Exception e) {
                listener.onExceptionToReOpen(e);
            }

            @Override
            public void onLogMessage(String message, @Nullable Exception e) {
                listener.onLogMessage(message, e);
                Log.d(TAG, message, e);
            }

            @Override
            public void onStatusChange(@NonNull Status status) {
                listener.onStatusChange(status);
            }
        });
    }

    public void start() {
        mTextService.start();
    }


    public void stop() {
        Log.d(TAG, "终止连接", null);
        mTextService.stop();
        mExecutorForWrite.shutdown();
    }


    public boolean isConnection() {
        if (mTextService != null) {
            return mTextService.isConnected();
        }
        return false;
    }

    public void sendMessage(final String message, @CmdConstant.TYPE final int type) {
        sendMessage(message.getBytes(), type);
    }

    public void sendMessage(final byte[] bytes, @CmdConstant.TYPE final int type) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mTextService.writeBytes(bytes, (byte) type);
            }
        });
    }


}
