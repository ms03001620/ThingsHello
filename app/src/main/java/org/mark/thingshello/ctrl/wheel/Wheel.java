package org.mark.thingshello.ctrl.wheel;

import com.google.android.things.pio.Gpio;
import com.leinardi.android.things.pio.SoftPwm;

import java.io.IOException;

/**
 * Created by Mark on 2018/7/25
 */
public class Wheel {
    private final static int DEFAULT_SPEED = 10;
    private Gpio in2;
    private Gpio in1;
    private SoftPwm pwmSpeed;
    private int speed = DEFAULT_SPEED;



    public Wheel(Gpio forward, Gpio back, SoftPwm speed) {
        in2 = forward;
        in1 = back;
        pwmSpeed = speed;

        try {
            in1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            in2.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            pwmSpeed.setEnabled(true);
            pwmSpeed.setPwmFrequencyHz(300);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forward() {
        try {
            in1.setDirection(Gpio.ACTIVE_HIGH);
            in2.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void back() {
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
            in2.close();
            in1.close();
            pwmSpeed.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void forwardBuff() {
        try {
            in2.setDirection(Gpio.ACTIVE_HIGH);
            in1.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
