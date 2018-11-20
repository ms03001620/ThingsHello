package org.mark.thingshello.ctrl.wheel;

import com.google.android.things.pio.Gpio;
import com.leinardi.android.things.pio.SoftPwm;

import java.io.IOException;

/**
 * 轮
 */
public class Wheel {
    private Gpio in2;
    private Gpio in1;
    private SoftPwm pwmSpeed;

    public Wheel(Gpio forward, Gpio back, SoftPwm softPwmSpeed) {
        in2 = forward;
        in1 = back;
        pwmSpeed = softPwmSpeed;

        try {
            in1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            in2.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            pwmSpeed.setEnabled(true);
            pwmSpeed.setPwmFrequencyHz(300);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forward(int speed) {
        try {
            in1.setDirection(Gpio.ACTIVE_HIGH);
            in2.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void back(int speed) {
        try {
            in1.setDirection(Gpio.ACTIVE_LOW);
            in2.setDirection(Gpio.ACTIVE_HIGH);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            in2.setDirection(Gpio.ACTIVE_LOW);
            in1.setDirection(Gpio.ACTIVE_LOW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        try {
            in2.close();
            in1.close();
            pwmSpeed.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forwardBuff(int speed) {
        try {
            in2.setDirection(Gpio.ACTIVE_HIGH);
            in1.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
