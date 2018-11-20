package org.mark.thingshello.ctrl.wheel;

import android.support.annotation.NonNull;

import com.google.android.things.pio.PeripheralManager;
import com.leinardi.android.things.pio.SoftPwm;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.thingshello.ctrl.BoardDefaults;
import org.mark.thingshello.ctrl.OnReceiverCommand;

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
public class WheelAction extends OnReceiverCommand {
    private Wheel wheelLeft;
    private Wheel wheelRight;


    public WheelAction() throws Exception {
        super();
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

    /**
     * 开始行走，提供做右轮速度，负值为向后行驶速度
     */
    public void run(int speedLeft, int speedRight) {
        if (speedLeft == 0 && speedRight == 0) {
            stop();
            return;
        }

        if (speedLeft >= 0) {
            wheelLeft.forward(speedLeft);
        } else {
            wheelLeft.back(Math.abs(speedLeft));
        }

        if (speedRight >= 0) {
            wheelRight.forward(speedRight);
        } else {
            wheelRight.back(Math.abs(speedRight));
        }
    }

    /**
     * 停车
     */
    public void stop() {
        wheelLeft.stop();
        wheelRight.stop();
    }

    /**
     * 原地左转
     */
    public void rotateLeft(int speed) {
        wheelLeft.forwardBuff(speed);
        wheelRight.forward(speed);
    }

    /**
     * 原地右转
     */
    public void rotateRight(int speed) {
        wheelLeft.forward(speed);
        wheelRight.forwardBuff(speed);
    }

    @Override
    public void release() {
        wheelLeft.release();
        wheelRight.release();
    }

    @Override
    public void onCommand(@NonNull String json, @CmdConstant.TYPE int type) {
        if (type == CmdConstant.WHEEL) {
            WheelCmd direction = gson.fromJson(json, WheelCmd.class);
            run(direction.getLeft(), direction.getRight());
        }

    }
}
