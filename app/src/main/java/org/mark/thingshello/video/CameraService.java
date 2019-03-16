package org.mark.thingshello.video;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

import org.mark.camera.DoorbellCamera;

/**
 * Created by Mark on 2018/8/16
 * 0 = {Size@5230} "176x144"
 * 1 = {Size@5231} "320x240"
 * 2 = {Size@5232} "640x480"
 * 3 = {Size@5233} "1024x768"
 * 4 = {Size@5234} "1280x720"
 * 5 = {Size@5235} "1640x1232"
 * 6 = {Size@5236} "1920x1080"
 * 7 = {Size@5237} "2560x1440"
 * <p>
 * 0 = {Size@5321} "160x120"
 * 1 = {Size@5322} "176x144"
 * 2 = {Size@5323} "320x240"
 * 3 = {Size@5324} "352x288"
 * 4 = {Size@5325} "640x480"
 */

public class CameraService extends Service {
    public static final String TAG = "CameraService";
    private Messenger mMessenger;
    private CameraPresent mCameraPresent;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mCameraPresent = new CameraPresent(this);
        mMessenger = new Messenger(mCameraPresent.getHandler());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        DoorbellCamera.getInstance().shutDown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mCameraPresent.release();
        super.onDestroy();
    }
}
