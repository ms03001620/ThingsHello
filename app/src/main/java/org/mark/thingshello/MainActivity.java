package org.mark.thingshello;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.DeviceManager;
import org.mark.thingshello.ctrl.SimpleJsonReceiver;
import org.mark.thingshello.video.CameraService;
import org.mark.thingshello.video.CameraServiceConnection;

import androidx.annotation.Nullable;

/**
 * 初始化三大模块
 * 1.Device 车硬件
 * 2.Camera 摄像头
 * 3.Socket 遥控通信
 */
public class MainActivity extends Activity {
    @Nullable
    private DeviceManager mDeviceManager;
    @Nullable
    private CameraServiceConnection mCameraServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindDevice();
        bindCameraService();
        startSocket();
    }

    private void startSocket(){
        SocketManager.getInstance().init(new SimpleJsonReceiver() {
            @Override
            public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
                Log.d("DeviceManager", "onReceiveMessage:" + json.length() + ", type:" + type);
                if (mDeviceManager != null) {
                    mDeviceManager.onCommand(json, type);
                }
                if (mCameraServiceConnection != null) {
                    mCameraServiceConnection.onCommand(json, type);
                }
            }
        });
        SocketManager.getInstance().start();
    }

    private void stopSocket(){
        SocketManager.getInstance().stop();
    }

    private void bindDevice() {
        mDeviceManager = new DeviceManager();
    }

    private void unbindDevice(){
        if (mDeviceManager != null) {
            mDeviceManager.release();
        }
    }

    private void bindCameraService() {
        mCameraServiceConnection = new CameraServiceConnection();
        Intent intent = new Intent(this, CameraService.class);
        bindService(intent, mCameraServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindCameraService(){
        if (mCameraServiceConnection != null) {
            unbindService(mCameraServiceConnection);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");

        stopSocket();
        unbindCameraService();
        unbindDevice();
        super.onDestroy();
    }

}
