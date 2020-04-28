package org.mark.thingshello.ctrl.servo;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

/**
 * 摄像头舵机
 */
public class CameraServo extends OnReceiverCommand {
    private double mActivePulseDuration;

    private static final double MIN_ACTIVE_PULSE_DURATION_MS = 0;
    private static final double MAX_ACTIVE_PULSE_DURATION_MS = 1.8;
    private static final double PULSE_PERIOD_MS = 20;  // Frequency of 50Hz (1000/20)

    // Parameters for the servo movement over time
    private static final double PULSE_CHANGE_PER_STEP_MS = 0.2;
    private static final int INTERVAL_BETWEEN_STEPS_MS = 100;

    Pwm mPwm;

    public CameraServo() throws IOException {
        try {
            mActivePulseDuration = MIN_ACTIVE_PULSE_DURATION_MS;


           // SoftPwm softPwm = SoftPwm.openSoftPwm("BCM13");

           // PeripheralManager.getInstance().openGpio("BCM13");

          //  List<String> list = PeripheralManager.getInstance().getGpioList();


          //  Log.d("CameraServo", "list" + list.toString());

          mPwm = PeripheralManager.getInstance().openPwm("PWM0");

          //  mPwm = PeripheralManager.getInstance().openPwm("PWM1");

            Log.d("CameraServo", "pwm name:" + mPwm.getName());

            // Always set frequency and initial duty cycle before enabling PWM
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
                try {
                    float d = convert(0, 2, cameraServoCmd.getProgress());
                    Log.d("CameraServo", "progress:" + cameraServoCmd.getProgress() + ", d:" + d);

                    mPwm.setPwmDutyCycle(100 * d / PULSE_PERIOD_MS);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void release() {
        try {
            mPwm.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mPwm = null;
        }
    }


    public static float convert(int min, int max, int current){
        int t = max - min;
        float dt = t/100.0f;
        return min + dt * current;
    }
}
