package org.mark.thingshello.ctrl.wheel;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.leinardi.android.things.pio.SoftPwm;

import org.mark.thingshello.ctrl.BoardDefaults;

import java.io.IOException;

/**
 * 轮
 */
public class Wheel {
    private Gpio in2;
    private Gpio in1;
    private SoftPwm pwmSpeed;

    int forwardPin;
    int backPin;
    int softPwmSpeedPin;

    private boolean hasBind;

    public Wheel(int forwardPin, int backPin, int softPwmSpeedPin) {
        this.forwardPin = forwardPin;
        this.backPin = backPin;
        this.softPwmSpeedPin = softPwmSpeedPin;
    }

    public void bindPin() {
        if (hasBind) {
            return;
        }
        Log.d("Wheel", "bindPin");
        hasBind = true;
        PeripheralManager pioService = PeripheralManager.getInstance();
        try {
            in2 = pioService.openGpio(BoardDefaults.getRpi3GPIO(forwardPin));
            in1 = pioService.openGpio(BoardDefaults.getRpi3GPIO(backPin));
            pwmSpeed = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(softPwmSpeedPin));

            in1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            in2.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            pwmSpeed.setEnabled(true);
            pwmSpeed.setPwmFrequencyHz(300);
        } catch (Exception e) {
            hasBind = false;
            Log.e("Wheel", "bind", e);
        }
    }

    public void unBindPin() {
        if (hasBind) {
            release();
        }
    }

    public void forward(int speed) {
        try {
            Log.d("Wheel", "forward");
            in1.setDirection(Gpio.ACTIVE_HIGH);
            in2.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void back(int speed) {
        try {
            Log.d("Wheel", "back");
            in1.setDirection(Gpio.ACTIVE_LOW);
            in2.setDirection(Gpio.ACTIVE_HIGH);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (in2 == null || in1 == null) {
            return;
        }
        try {
            Log.d("Wheel", "stop");
            in2.setDirection(Gpio.ACTIVE_LOW);
            in1.setDirection(Gpio.ACTIVE_LOW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void forwardBuff(int speed) {
        try {
            Log.d("Wheel", "forwardBuff");
            in2.setDirection(Gpio.ACTIVE_HIGH);
            in1.setDirection(Gpio.ACTIVE_LOW);
            pwmSpeed.setPwmDutyCycle(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void release() {
        try {
            Log.d("Wheel", "release");
            in2.close();
            in1.close();
            pwmSpeed.setEnabled(false);
            pwmSpeed.close();
            hasBind = false;
        } catch (IOException e) {
            Log.e("Wheel", "release", e);
        }
    }

    public boolean isHasBind(){
        return hasBind;
    }


}
