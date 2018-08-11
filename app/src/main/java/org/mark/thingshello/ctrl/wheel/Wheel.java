package org.mark.thingshello.ctrl.wheel;

import com.google.android.things.pio.Gpio;
import com.leinardi.android.things.pio.SoftPwm;

import java.io.IOException;

/**
 * Created by Mark on 2018/7/25
 */
public class Wheel {
    private final static int DEFAULT_SPEED = 50;
    private Gpio gpioForward;
    private Gpio gpioBack;
    private SoftPwm pwmSpeed;
    private int speed = DEFAULT_SPEED;



    public Wheel(Gpio forward, Gpio back, SoftPwm speed) {
        gpioForward = forward;
        gpioBack = back;
        pwmSpeed = speed;

        try {
            gpioForward.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            gpioBack.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            pwmSpeed.setEnabled(true);
            pwmSpeed.setPwmFrequencyHz(300);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forward() {
        try {
            gpioForward.setValue(false);
            gpioBack.setValue(true);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void back() {
        try {
            gpioForward.setValue(true);
            gpioBack.setValue(false);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            gpioForward.setValue(false);
            gpioBack.setValue(false);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getSpeed() {
        return speed;
    }

    public void addSpeed(int buff) {
        int temp = speed + buff;

        if (temp > SoftPwm.MAX_FREQ) {
            temp = SoftPwm.MAX_FREQ;
        }

        speed = temp;
    }

    public void release() {
        try {
            gpioForward.close();
            gpioBack.close();
            pwmSpeed.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forwardBuff() {
        try {
            gpioForward.setDirection(Gpio.ACTIVE_HIGH);
            gpioBack.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
