package org.mark.mobile.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.ConnectedThread;

/**
 * Created by Mark on 2018/8/10
 */
public class ConnectedWifi implements IConnect {
    private ConnectedThread mConnectThread;

    public ConnectedWifi(String host, int port, ClientMessageCallback responseListener) {
        mConnectThread = new ConnectedThread(host, port, responseListener);
    }

    public void start() {
        mConnectThread.start();
    }

    @Override
    public void stop() {
        mConnectThread.stop(true);
    }

    public void sendMessage(final String message) {
        mConnectThread.write(message, (byte) 2);
    }

    @Override
    public void release() {
        mConnectThread.stop(true);
        mConnectThread = null;
    }

    public void reg(Context context) {
        IntentFilter networkFileter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(new NetworkStateReceiver(), networkFileter);
    }

    public class NetworkStateReceiver extends BroadcastReceiver {
        public NetworkStateReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            // 网络变化
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                final NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.isAvailable()) {

                } else {

                }
            }
        }
    }
}
