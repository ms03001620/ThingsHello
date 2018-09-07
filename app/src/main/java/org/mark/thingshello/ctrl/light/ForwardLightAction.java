package org.mark.thingshello.ctrl.light;

import android.support.annotation.NonNull;

import com.google.android.things.pio.PeripheralManager;
import com.leinardi.android.things.pio.SoftPwm;

import org.mark.base.CommandConstant;
import org.mark.thingshello.ctrl.BoardDefaults;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

/**
 * Created by Mark on 2018/8/12
 */
public class ForwardLightAction extends OnReceiverCommand {
    // LED_R = 22
    // LED_G = 27
    // LED_B = 24
    private SoftPwm pwmRed;
    private SoftPwm pwmGreen;
    private SoftPwm pwmBlue;

    public ForwardLightAction() throws IOException {
        PeripheralManager pioService = PeripheralManager.getInstance();

        pwmRed = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(22));
        pwmRed.setPwmFrequencyHz(300);

        pwmGreen = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(27));
        pwmGreen.setPwmFrequencyHz(300);

        pwmBlue = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(24));
        pwmBlue.setPwmFrequencyHz(300);
    }

    public void test() {
        try {
            pwmRed.setEnabled(true);
            pwmRed.setPwmDutyCycle(100);

            pwmGreen.setEnabled(true);
            pwmGreen.setPwmDutyCycle(100);

            pwmBlue.setEnabled(true);
            pwmBlue.setPwmDutyCycle(100);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void testStop() {
        pwmRed.setPwmDutyCycle(0);
        pwmGreen.setPwmDutyCycle(0);
        pwmBlue.setPwmDutyCycle(0);
    }

    public void release() {
        try {
            pwmRed.close();
            pwmGreen.close();
            pwmBlue.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCommand(@NonNull byte[] bytes, int type) {
        int data = decodeByteAsInteger(bytes);
        switch (data){
            case CommandConstant.LIGHT.START:
                test();
                break;
            case CommandConstant.LIGHT.STOP:
                testStop();
                break;
        }
    }
}
