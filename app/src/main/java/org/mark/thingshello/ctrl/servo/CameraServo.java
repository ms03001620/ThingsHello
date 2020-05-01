package org.mark.thingshello.ctrl.servo;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import org.mark.base.thread.WorkThreadHandler;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

/**
 * 摄像头舵机
 */
public class CameraServo extends OnReceiverCommand {
    private static final double PULSE_PERIOD_MS = 20;  // Frequency of 50Hz (1000/20)
    private Pwm mPwm;

    WorkThreadHandler mWorkThread;

    public CameraServo() {
        mWorkThread = new WorkThreadHandler();
    }

    private void bindPin() {
        try {
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
                if (mPwm == null) {
                    bindPin();
                }

                try {
                    float d = convert(0, 2, cameraServoCmd.getProgress());
                    Log.d("CameraServo", "progress:" + cameraServoCmd.getProgress() + ", dp:" + d);
                    mPwm.setPwmDutyCycle(100 * d / PULSE_PERIOD_MS);
                    mWorkThread.removeRunnable(autoStop);
                    mWorkThread.runWorkThreadDelay(autoStop, 500);
                } catch (Exception e) {
                    Log.e("CameraServo", "onCommand", e);
                }
            }
        }
    }

    private Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            Log.d("CameraServo", "autoStop");
            unBindPin();
        }
    };


    private void unBindPin() {
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


    @Override
    public void release() {
        unBindPin();
        mWorkThread.release();
    }


    public static float convert(int min, int max, int current) {
        int t = max - min;
        float dt = t / 100.0f;
        return min + dt * current;
    }
}
