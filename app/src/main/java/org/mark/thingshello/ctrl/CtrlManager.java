package org.mark.thingshello.ctrl;

import android.util.Log;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.MainActivity;
import org.mark.thingshello.ctrl.light.ForwardLightAction;
import org.mark.thingshello.ctrl.voice.BuzzerAction;
import org.mark.thingshello.ctrl.wheel.WheelAction;
import org.mark.thingshello.video.CameraAction;

/**
 * Created by Mark on 2018/7/25
 * adb shell am startservice -n com.google.wifisetup/.WifiSetupService -a WifiSetupService.Connect -e ssid "Xiaomi_5377" -e passphrase "nono12345"
 */
public class CtrlManager {
    private WheelAction mActionWheel;
    private BuzzerAction mBuzzerAction;
    private ForwardLightAction mForwardLightAction;
    private CameraAction mCameraAction;

    public CtrlManager(final MainActivity.OnCtrlResponse listener) throws Exception {
        mActionWheel = new WheelAction();
        mBuzzerAction = new BuzzerAction();
        mForwardLightAction = new ForwardLightAction();
        mCameraAction = new CameraAction(listener);

        SocketManager.getInstance().init(new SocketManager.OnReceiveMessage() {
            @Override
            public void onReceiveMessage(final byte[] bytes, int type) {
                listener.onReceiveMessage(bytes, type);
                SocketManager.getInstance().send(bytes);
                Log.d("CtrlManager", "onReceiveMessage:" + bytes.length + ", type:" + type);
                try {
                    switch (type) {
                        case 1:
                            mActionWheel.setSpeed(Integer.valueOf(new String(bytes)));
                            break;
                        case 2:
                            mCommandReceiver.onCommand(Integer.valueOf(new String(bytes)));
                            break;
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        SocketManager.getInstance().start();
        // 告知系统已就绪
        mCommandReceiver.onCommand(8);
    }


    private OnReceiverCommand mCommandReceiver = new OnReceiverCommand() {
        @Override
        public void onCommand(int code) {
            switch (code) {
                case 0:
                    mActionWheel.stop();
                    break;
                case 1:
                    mActionWheel.forward();
                    break;
                case 2:
                    mActionWheel.back();
                    break;
                case 3:
                    mActionWheel.left();
                    break;
                case 4:
                    mActionWheel.right();
                    break;
                case 8:
                    mBuzzerAction.di();
                    break;
                case 9:
                    mBuzzerAction.stop();
                    break;
                case 10:
                    mForwardLightAction.test();
                    break;
                case 11:
                    mForwardLightAction.testStop();
                    break;
                case 12:
                    mCameraAction.startPreview();
                    break;
                case 13:
                    mCameraAction.stopPreview();
                    break;
            }
        }
    };

    public void release() {
        SocketManager.getInstance().stop();
        mActionWheel.release();
        mBuzzerAction.release();
        mForwardLightAction.release();
    }
}
