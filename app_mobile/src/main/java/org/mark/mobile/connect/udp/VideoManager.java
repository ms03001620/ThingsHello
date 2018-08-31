package org.mark.mobile.connect.udp;

import android.content.Context;
import android.widget.VideoView;

import org.mark.lib_unit_socket.ClientMessageCallback;

/**
 * Created by Mark on 2018/8/31
 */
public class VideoManager implements IReceiver {

    private IReceiver mIReceiver;

    public VideoManager(String name, Context context) {
        if ("tcp".equals(name)) {
            mIReceiver = new TcpReceiver();
        }

        if ("udp".equals(name)) {
            mIReceiver = new UdpReceiver(context);
        }
    }

    @Override
    public void start() {
        mIReceiver.start();
    }

    @Override
    public void stop() {
        mIReceiver.stop();
    }

    @Override
    public void addCallback(ClientMessageCallback callback) {
        mIReceiver.addCallback(callback);
    }

    @Override
    public void removeCallback(ClientMessageCallback callback) {
        mIReceiver.removeCallback(callback);
    }
}
