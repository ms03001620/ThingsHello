package org.mark.thingshello.ctrl;

import android.app.Activity;
import android.util.Log;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.ctrl.light.ForwardLightAction;
import org.mark.thingshello.ctrl.voice.BuzzerAction;
import org.mark.thingshello.ctrl.wheel.IWheelAction;
import org.mark.thingshello.ctrl.wheel.WheelAction;

/**
 * Created by Mark on 2018/7/25
 * adb shell am startservice -n com.google.wifisetup/.WifiSetupService -a WifiSetupService.Connect -e ssid "Xiaomi_5377" -e passphrase "nono12345"
 */
public class CtrlManager {
    private WheelAction mActionWheel;
    private BuzzerAction mBuzzerAction;
    private ForwardLightAction mForwardLightAction;

    public CtrlManager(Activity activity, final SocketManager.OnReceiveMessage listener) throws Exception{
        mActionWheel = new WheelAction();
        mBuzzerAction = new BuzzerAction();
        mForwardLightAction = new ForwardLightAction();

        SocketManager.getInstance().init(new SocketManager.OnReceiveMessage() {
            @Override
            public void onReceiveMessage(final byte[] bytes, int type) {
                listener.onReceiveMessage(bytes, type);
                Log.d("CtrlManager", "onReceiveMessage:" + bytes.length + ", type:" + type);
                try {
                    switch (type){
                        case 1:
                            mActionWheel.setSpeed(Integer.valueOf(new String(bytes)));
                            break;
                        case 2:
                            mCommandReceiver.onCommand(Integer.valueOf(new String(bytes)));
                            break;
                        case 3:
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
            switch (code){
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
