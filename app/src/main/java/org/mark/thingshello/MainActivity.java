package org.mark.thingshello;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.ctrl.CtrlManager;
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
    private CtrlManager mCtrlManager;
    private TextView mTextLog;
    private ImageView mImageView;
    private Messenger mService = null;

    public interface OnCtrlResponse{
        void onReceiveMessage(final byte[] message, int type);

        @Nullable
        Messenger getMessenger();

        ImageView getImage();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextLog = findViewById(R.id.text);
        mImageView = findViewById(R.id.image);

        try {
            mCtrlManager = new CtrlManager(new OnCtrlResponse() {
                @Override
                public void onReceiveMessage(final byte[] message, int type) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTextLog.setText(message.length + ", " + System.currentTimeMillis());
                        }
                    });
                }

                @Override
                public Messenger getMessenger() {
                    return mService;
                }

                @Override
                public ImageView getImage() {
                    return mImageView;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }

        Intent intent = new Intent(this, CameraService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

    }

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };

    @Override
    protected void onDestroy() {
        if (mCtrlManager != null) {
            mCtrlManager.release();
        }
        if (mService != null) {
            unbindService(mServiceConnection);
        }
        super.onDestroy();
    }


}
