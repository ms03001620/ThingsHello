package org.mark.thingshello;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mark.thingshello.ctrl.DeviceManager;
import org.mark.thingshello.video.CameraAction;
import org.mark.thingshello.video.CameraService;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {
    @Nullable
    private DeviceManager mDeviceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindDevice();
    }

    private void bindDevice() {
        try {
            mDeviceManager = new DeviceManager();
            bindService();
        } catch (Exception e) {
            Log.e("MainActivity", "init DeviceManager", e);
            finish();
        }
    }

    private void bindService() {
        Intent intent = new Intent(this, CameraService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection mServiceConnection = new ServiceConnection() {
        CameraAction cameraAction;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("MainActivity", "onServiceConnected");
            Messenger messenger = new Messenger(iBinder);
            cameraAction = new CameraAction(messenger);
            mDeviceManager.add(cameraAction);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("MainActivity", "onServiceDisconnected");
            mDeviceManager.remove(cameraAction);
        }
    };

    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");
        if (mDeviceManager != null) {
            mDeviceManager.release();
        }
        unbindService(mServiceConnection);
        super.onDestroy();
    }


}
