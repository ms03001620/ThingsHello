package org.mark.thingshello.video;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;

/**
 * Created by mark on 2020/10/15
 */

public class CameraServiceConnection implements ServiceConnection {
    @Nullable
    private CameraAction cameraAction;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, final IBinder iBinder) {
            Log.d("CameraServiceConnection", "onServiceConnected");
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        iBinder.unlinkToDeath(this, 0);
                        cameraAction = null;
                        Log.w("CameraServiceConnection", "DeathRecipient");
                    }
                }, 0);
            } catch (RemoteException e) {
                Log.e("CameraServiceConnection", "DeathRecipient", e);
            }
            Messenger messenger = new Messenger(iBinder);
            cameraAction = new CameraAction(messenger);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("CameraServiceConnection", "onServiceDisconnected");
            if (cameraAction != null) {
                cameraAction.release();
                cameraAction = null;
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mServiceConnection.onServiceConnected(name, service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mServiceConnection.onServiceDisconnected(name);
    }

    public void onCommand(String json, int type) {
        if (type != CmdConstant.CAMERA) {
            return;
        }
        if (cameraAction == null) {
            Log.w("CameraServiceConnection", "camera service not start, can not process json:" + json);
            return;
        }
        cameraAction.onCommand(json, type);
    }
}
