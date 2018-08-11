package org.mark.thingshello;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import org.mark.lib_unit_socket.SocketManager;
import org.mark.thingshello.ctrl.CtrlManager;

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
    private CtrlManager mCtrlManager;
    private TextView mTextLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextLog = findViewById(R.id.text);

        mCtrlManager = new CtrlManager(this, new SocketManager.OnReceiveMessage() {
            @Override
            public void onReceiveMessage(final String message, int type) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextLog.setText(message+", "+System.currentTimeMillis());
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        mCtrlManager.release();
        super.onDestroy();
    }
}
