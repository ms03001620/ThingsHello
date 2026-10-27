package com.leinardi.android.things.pio;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import java.io.IOException;

public class SoftPwm {
    private Gpio mGpio;
    private double mFrequencyHz = 50;
    private double mDutyCycle = 0;
    private boolean mEnabled = false;
    private Thread mThread;

    private SoftPwm(String pin) throws IOException {
        mGpio = PeripheralManager.getInstance().openGpio(pin);
        mGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
    }

    public static SoftPwm openSoftPwm(String pin) throws IOException {
        return new SoftPwm(pin);
    }

    public void setPwmFrequencyHz(double freq) throws IOException {
        mFrequencyHz = freq;
    }

    public void setEnabled(boolean enabled) throws IOException {
        mEnabled = enabled;
        if (enabled && (mThread == null || !mThread.isAlive())) {
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mEnabled) {
                        try {
                            double periodMs = 1000.0 / mFrequencyHz;
                            double highMs = periodMs * (mDutyCycle / 100.0);
                            double lowMs = periodMs - highMs;
                            
                            if (highMs > 0) {
                                mGpio.setValue(true);
                                Thread.sleep((long) highMs, (int) ((highMs % 1) * 1000000));
                            }
                            if (lowMs > 0) {
                                mGpio.setValue(false);
                                Thread.sleep((long) lowMs, (int) ((lowMs % 1) * 1000000));
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            });
            mThread.start();
        } else if (!enabled && mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    public void setPwmDutyCycle(double dutyCycle) throws IOException {
        mDutyCycle = dutyCycle;
    }

    public void close() throws IOException {
        mEnabled = false;
        if (mThread != null) {
            mThread.interrupt();
        }
        if (mGpio != null) {
            mGpio.close();
        }
    }
}
