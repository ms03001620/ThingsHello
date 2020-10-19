package org.mark.thingshello.ctrl.servo;

import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import org.mark.base.thread.WorkThreadHandler;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.OnReceiverCommand;
import org.mark.thingshello.ctrl.comp.bind.Bindable;
import org.mark.thingshello.ctrl.comp.bind.ExclusiveBind;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * 摄像头舵机
 */
public class CameraServo extends OnReceiverCommand implements Bindable {
    private static final double PULSE_PERIOD_MS = 20;  // Frequency of 50Hz (1000/20)
    private Pwm mPwm;
    ExclusiveBind exclusiveBind;
    WorkThreadHandler autoUnbindThread;

    public CameraServo(ExclusiveBind exclusiveBind) {
        this.exclusiveBind = exclusiveBind;
    }

    private void init() {
        try {
            autoUnbindThread = new WorkThreadHandler();
            Log.d("CameraServo", "init");
            mPwm = PeripheralManager.getInstance().openPwm("PWM0");
            mPwm.setPwmFrequencyHz(1000 / PULSE_PERIOD_MS);
            mPwm.setEnabled(true);
        } catch (Exception e) {
            Log.e("CameraServo", "init", e);
        }
    }

    @Override
    public void onCommand(@NonNull String json, int type) {
        if (type == CmdConstant.CAMERA_SERVO) {
            CameraServoCmd cameraServoCmd = gson.fromJson(json, CameraServoCmd.class);
            if (cameraServoCmd != null) {
                exclusiveBind.activeBindable(this);
                if (mPwm == null) {
                    Log.w("CameraServo", "bind error pwm null");
                    return;
                }

                try {
                    float d = convert(0.8f, 1.5f, cameraServoCmd.getProgress());
                    Log.d("CameraServo", "progress:" + cameraServoCmd.getProgress() + ", dp:" + d);
                    mPwm.setPwmDutyCycle(100 * d / PULSE_PERIOD_MS);

                    autoUnbindThread.removeRunnable(autoStopRunnable);
                    autoUnbindThread.runWorkThreadDelay(autoStopRunnable, 500);
                } catch (Exception e) {
                    Log.e("CameraServo", "onCommand", e);
                }
            }
        }
    }

    @Override
    public void release() {
        stop();
        autoUnbindThread.release();
    }

    private void stop() {
        if (mPwm == null) {
            return;
        }
        try {
            mPwm.setEnabled(false);
            mPwm.close();
        } catch (IOException e) {
            Log.e("CameraServo", "unBindPin", e);
        } finally {
            mPwm = null;
        }
    }


    public static float convert(float min, float max, int current) {
        float t = max - min;
        float dt = t / 100.0f;
        return min + dt * current;
    }

    @Override
    public void onBind() {
        init();
    }

    @Override
    public void onUnBind() {
        release();
    }

    Runnable autoStopRunnable = new Runnable() {
        @Override
        public void run() {
            exclusiveBind.release(CameraServo.this);
        }
    };

}
