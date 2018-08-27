package org.mark.mobile.connect;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.lib_unit_socket.ClientMessageCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Mark on 2018/8/10
 */
public class ConnectedManager {
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
            instance = new ConnectedManager();
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

    public void sendMessage(final String message) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                if (mDefaultConnect != null) {
                    mDefaultConnect.sendMessage(message);
                }
            }
        });
    }

    public void sendMessage(final String message, final int type) {
        mExecutorForWrite.execute(new Runnable() {
            @Override
            public void run() {
                if (mDefaultConnect != null) {
                    mDefaultConnect.sendMessage(message, type);
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
        if (mCallbackList.contains(callback)) {
            mCallbackList.remove(callback);
        }
    }
}
