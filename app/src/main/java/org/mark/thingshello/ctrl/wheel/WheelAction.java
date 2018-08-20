package org.mark.thingshello.ctrl.wheel;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.things.pio.PeripheralManager;
import com.leinardi.android.things.pio.SoftPwm;

import org.mark.thingshello.ctrl.BoardDefaults;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

/**
 * AIN1-----40----29(wiringPi编码)--21
 * AIN2-----38----28(wiringPi编码)--20
 * <p>
 * BIN1-----37----25(wiringPi编码)--26
 * BIN2-----35----24(wiringPi编码)--19
 * <p>
 * PWMA-----36----27(wiringPi编码)--16
 * PWMB-----33----23(wiringPi编码)--13
 */
public class WheelAction extends OnReceiverCommand implements IWheelAction {
    private Wheel wheelLeft;
    private Wheel wheelRight;

    public WheelAction() throws Exception {
        PeripheralManager pioService = PeripheralManager.getInstance();

        wheelLeft = new Wheel(
                pioService.openGpio(BoardDefaults.getRpi3GPIO(21)),
                pioService.openGpio(BoardDefaults.getRpi3GPIO(20)),
                SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(16))
        );

        wheelRight = new Wheel(
                pioService.openGpio(BoardDefaults.getRpi3GPIO(26)),
                pioService.openGpio(BoardDefaults.getRpi3GPIO(19)),
                SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(13))
        );
    }

    @Override
    public void forward() {
        wheelLeft.forward();
        wheelRight.forward();
    }

    @Override
    public void back() {
        wheelLeft.back();
        wheelRight.back();
    }

    @Override
    public void stop() {
        wheelLeft.stop();
        wheelRight.stop();
    }

    @Override
    public void left() {
        wheelLeft.forwardBuff();
        wheelRight.forward();
    }

    @Override
    public void right() {
        wheelLeft.forward();
        wheelRight.forwardBuff();
    }

    @Override
    public void rotateLeft() {

    }

    @Override
    public void rotateRight() {

    }

    @Override
    public void release() {
        wheelLeft.release();
        wheelRight.release();
    }

    public void setSpeed(int speed) {
        wheelLeft.setSpeed(speed);
        wheelRight.setSpeed(speed);
    }

    @Override
    public void onCommand(@NonNull byte[] bytes, int type) {
        int data = decodeByteAsInteger(bytes);
        if (type == 1) {
            setSpeed(data);
            return;
        }

        switch (data) {
            case 0:
                stop();
                break;
            case 1:
                forward();
                break;
            case 2:
                back();
                break;
            case 3:
                left();
                break;
            case 4:
                right();
                break;
        }
    }
}
