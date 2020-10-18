package org.mark.thingshello.ctrl.light;

import com.leinardi.android.things.pio.SoftPwm;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.LightCmd;
import org.mark.thingshello.ctrl.BoardDefaults;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * 彩色前灯
 */
public class ForwardLightAction extends OnReceiverCommand {
    // LED_R = 22
    // LED_G = 27
    // LED_B = 24
    private SoftPwm pwmRed;
    private SoftPwm pwmGreen;
    private SoftPwm pwmBlue;

    public ForwardLightAction() throws IOException {

        pwmRed = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(22));
        pwmRed.setPwmFrequencyHz(300);

        pwmGreen = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(27));
        pwmGreen.setPwmFrequencyHz(300);

        pwmBlue = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(24));
        pwmBlue.setPwmFrequencyHz(300);
    }

    public void enable(int r, int g, int b) {
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

    public void disable() {
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
    public void onCommand(@NonNull String json, @CmdConstant.TYPE int type) {
        if (type == CmdConstant.LIGHT) {
            LightCmd lightCmd = gson.fromJson(json, LightCmd.class);

            if (lightCmd.isEnable()) {
                enable(lightCmd.getRed(), lightCmd.getGreen(), lightCmd.getBlue());
            } else {
                disable();
            }
        }
    }
}
