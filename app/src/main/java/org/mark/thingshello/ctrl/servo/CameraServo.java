package org.mark.thingshello.ctrl.servo;

import android.support.annotation.NonNull;

import com.leinardi.android.things.pio.SoftPwm;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.thingshello.ctrl.BoardDefaults;
import org.mark.thingshello.ctrl.OnReceiverCommand;

import java.io.IOException;

/**
 * 摄像头舵机
 */
public class CameraServo extends OnReceiverCommand {
    /**
     * 水平舵机
     */
    private Servo servoH;
    /**
     * 垂直舵机
     */
    private Servo servoV;

    public CameraServo() throws IOException {
        servoH = new Servo(11);
        servoV = new Servo(9);
    }


    @Override
    public void onCommand(@NonNull String json, int type) {
        if (type == CmdConstant.CAMERA_SERVO) {
            CameraServoCmd cameraServoCmd = gson.fromJson(json, CameraServoCmd.class);
            if (cameraServoCmd != null) {
                servoH.changePos(cameraServoCmd.getHorizontal());
                servoV.changePos(cameraServoCmd.getVertical());
            }
        }
    }

    @Override
    public void release() {
        servoH.release();
        servoV.release();
    }
}
