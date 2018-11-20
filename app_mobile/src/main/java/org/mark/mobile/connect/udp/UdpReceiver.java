package org.mark.mobile.connect.udp;

import android.content.Context;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.CameraCmd;
import org.mark.lib_unit_socket.udp.UdpClientThread;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.utils.PreferUtils;

/**
 * Created by Mark on 2018/8/31
 */
public class UdpReceiver {
    private UdpClientThread mUdpClientThread;

    public UdpReceiver(Context context, ClientMessageCallback callback) {
        PreferUtils utils = new PreferUtils(context);

        String[] ipx = utils.getAddress();
        if (ipx == null) {
            throw new IllegalArgumentException("无法在share中找到IP地址");
        }

        String ip = ipx[0];

        mUdpClientThread = new UdpClientThread(ip, 8000, callback);
    }


    public void start() {
        ConnectedManager.getInstance().sendObject(new CameraCmd(1), CmdConstant.CAMERA);

        mUdpClientThread.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 启动后主动给服务器发送hello信息。报告自己的ip
                mUdpClientThread.write("hello".getBytes(), 5);
            }
        }).start();
    }


    public void stop() {
        mUdpClientThread.stop(false);
        ConnectedManager.getInstance().sendObject(new CameraCmd(0), CmdConstant.CAMERA);
    }
}
