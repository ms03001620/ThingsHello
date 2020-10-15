package org.mark.thingshello.ctrl.wheel;

import android.support.annotation.NonNull;
import android.util.Log;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.lib_unit_socket.bean.WheelRotateCmd;
import org.mark.thingshello.ctrl.OnReceiverCommand;
import org.mark.thingshello.ctrl.comp.bind.Bindable;
import org.mark.thingshello.ctrl.comp.bind.ExclusiveBind;

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
public class WheelAction extends OnReceiverCommand implements Bindable {
    private Wheel wheelLeft;
    private Wheel wheelRight;
    ExclusiveBind exclusiveBind;


    public WheelAction(ExclusiveBind exclusiveBind) throws Exception {
        super();
        this.exclusiveBind = exclusiveBind;
        wheelLeft = new Wheel(21,20,16);
        wheelRight = new Wheel(26,19,13);
    }

    /**
     * 开始行走，提供做右轮速度，负值为向后行驶速度
     */
    private void run(int speedLeft, int speedRight) {
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
    private void stop() {
        wheelLeft.stop();
        wheelRight.stop();
    }

    /**
     * 原地左转
     */
    private void rotateLeft(int speed) {
        wheelLeft.forwardBuff(speed);
        wheelRight.forward(speed);
    }

    /**
     * 原地右转
     */
    private void rotateRight(int speed) {
        wheelLeft.forward(speed);
        wheelRight.forwardBuff(speed);
    }

    @Override
    public void release() {
        wheelLeft.unBindPin();
        wheelRight.unBindPin();
    }

    @Override
    public void onCommand(@NonNull String json, @CmdConstant.TYPE int type) {
        if (type != CmdConstant.WHEEL && type != CmdConstant.WHEEL_ROTATE) {
            return;
        }
        exclusiveBind.activeBindable(this);
        if (!isAllBind()) {
            Log.w("WheelAction", "bind error left:" + wheelLeft.isHasBind() + ", right:" + wheelRight.isHasBind());
            return;
        }

        if (type == CmdConstant.WHEEL) {
            WheelCmd direction = gson.fromJson(json, WheelCmd.class);
            run(direction.getLeft(), direction.getRight());
        } else if (type == CmdConstant.WHEEL_ROTATE) {
            WheelRotateCmd rotate = gson.fromJson(json, WheelRotateCmd.class);
            if (rotate.getSpeed() == 0) {
                stop();
                return;
            }

            if (rotate.getRotate() == WheelRotateCmd.Rotate.LEFT) {
                rotateLeft(rotate.getSpeed());
                return;
            }
            if (rotate.getRotate() == WheelRotateCmd.Rotate.RIGHT) {
                rotateRight(rotate.getSpeed());
                return;
            }
        }
    }

    @Override
    public void onBind() {
        wheelLeft.bindPin();
        wheelRight.bindPin();
    }

    @Override
    public void onUnBind() {
        wheelLeft.unBindPin();
        wheelRight.unBindPin();
    }

    public boolean isAllBind(){
        return wheelLeft.isHasBind() && wheelRight.isHasBind();
    }
}
