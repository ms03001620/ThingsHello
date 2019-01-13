package org.mark.thingshello.ctrl.servo;

import com.leinardi.android.things.pio.SoftPwm;

import org.mark.thingshello.ctrl.BoardDefaults;

import java.io.IOException;

/**
 * Created by mark on 2018/11/23
 */
public class Servo {

    private SoftPwm pwm;

    public Servo(int pin) throws IOException {
        pwm = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(pin));
        pwm.setPwmFrequencyHz(50);
    }

    public void changePos(int pos) {
        try {
            pwm.setEnabled(true);
            pwm.setPwmDutyCycle(pos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        try {
            pwm.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
