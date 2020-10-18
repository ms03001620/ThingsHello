package org.mark.mobile.connect;


import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.ClientMessageCallback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by Mark on 2018/8/10
 */
public class ConnectedManager extends CmdConstant {
    private static ConnectedManager instance;
    @Nullable
    private IConnect mDefaultConnect;
    private ExecutorService mExecutorForWrite;
    private List<ClientMessageCallback> mCallbackList;

    private ConnectedManager() {
        mExecutorForWrite = Executors.newCachedThreadPool();
        mCallbackList = new CopyOnWriteArrayList<>();
    }

    public static ConnectedManager getInstance() {
        if (instance == null) {
            synchronized (ConnectedManager.class) {
                if (instance == null) {
                    instance = new ConnectedManager();
                }
            }
        }
        return instance;
    }

    public void init(final String host, final int port) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                mDefaultConnect = new ConnectedWifi(host, port, new ClientMessageCallback() {
                    @Override
                    public void onReceiveMessage(byte[] bytes, int type) {
                        if (mCallbackList.size() > 0) {
                            for (ClientMessageCallback callback : mCallbackList) {
                                callback.onReceiveMessage(bytes, type);
                            }
                        }
                    }

                    @Override
                    public void onExceptionToReOpen(@NonNull final Exception e) {
                        if (mCallbackList.size() > 0) {
                            for (ClientMessageCallback callback : mCallbackList) {
                                callback.onExceptionToReOpen(e);
                            }
                        }
                    }

                    @Override
                    public void onLogMessage(String message, @Nullable Exception e) {
                        if (mCallbackList.size() > 0) {
                            for (ClientMessageCallback element : mCallbackList) {
                                element.onLogMessage(message, e);
                            }
                        }
                    }

                    @Override
                    public void onStatusChange(@NonNull Status status) {
                        if (mCallbackList.size() > 0) {
                            for (ClientMessageCallback callback : mCallbackList) {
                                callback.onStatusChange(status);
                            }
                        }
                    }
                });


                mDefaultConnect.start();

            }
        });
    }

    public void stop() {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                if (mDefaultConnect != null) {
                    mDefaultConnect.stop();
                }
            }
        });
    }

    public void addCallback(ClientMessageCallback callback) {
        if (!mCallbackList.contains(callback)) {
            mCallbackList.add(callback);
        }
    }

    public void removeCallback(ClientMessageCallback callback) {
        mCallbackList.remove(callback);
    }

    public void sendMessage(final String message, @CmdConstant.TYPE final int type) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                if (mDefaultConnect != null) {
                    mDefaultConnect.sendMessage(message, type);
                }
            }
        });
    }

}
