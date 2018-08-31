package org.mark.mobile.connect.udp;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.udp.UdpClientThread;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.utils.PreferUtils;

/**
 * Created by Mark on 2018/8/31
 */
public class UdpReceiver implements IReceiver {

    private UdpClientThread mUdpClientThread;

    @Nullable
    private ClientMessageCallback mClientMessageCallback;

    public UdpReceiver(Context context) {
        PreferUtils utils = new PreferUtils(context);

        String ip = utils.getHost();

        mUdpClientThread = new UdpClientThread(ip, 8000, new ClientMessageCallback() {
            @Override
            public void onReceiveMessage(byte[] bytes, int type) {
                if (mClientMessageCallback != null) {
                    mClientMessageCallback.onReceiveMessage(bytes, type);
                }
            }

            @Override
            public void onExceptionToReOpen(@NonNull Exception e) {
                if (mClientMessageCallback != null) {
                    mClientMessageCallback.onExceptionToReOpen(e);
                }
            }

            @Override
            public void onLogMessage(String message, @Nullable Exception e) {
                if (mClientMessageCallback != null) {
                    mClientMessageCallback.onLogMessage(message, e);
                }
            }

            @Override
            public void onStatusChange(@NonNull Status status) {
                if (mClientMessageCallback != null) {
                    mClientMessageCallback.onStatusChange(status);
                }
            }
        });
    }


    @Override
    public void start() {
        ConnectedManager.getInstance().sendMessage("12");

        mUdpClientThread.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mUdpClientThread.write("hello".getBytes(), 5);
            }
        }).start();
    }

    @Override
    public void stop() {
        mUdpClientThread.stop(false);
        ConnectedManager.getInstance().sendMessage("13");
    }

    @Override
    public void addCallback(ClientMessageCallback callback) {
        mClientMessageCallback = callback;
    }

    @Override
    public void removeCallback(ClientMessageCallback callback) {
        mClientMessageCallback = null;
    }
}
