package org.mark.mobile.preview;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;

/**
 * Created by mark on 2019/3/7
 */
public class ModelServo {
    private int h;
    private int v;

    enum Action {
        RIGHT, UP, LEFT, DOWN, STOP
    }

    public void action(Action action) {
        switch (action) {
            case UP:
                v++;
                break;
            case DOWN:
                v--;
                break;
            case LEFT:
                h--;
                break;
            case RIGHT:
                h++;
                break;
            case STOP:
                CameraServoCmd cameraCmd = new CameraServoCmd(0, 0);
                ConnectedManager.getInstance().sendObject(cameraCmd, CmdConstant.CAMERA_SERVO);
                return;
        }

        float vv = 2.5f + 10 * v / 180.0f;
        float hh = 2.5f + 10 * h / 180.0f;

        CameraServoCmd cameraCmd = new CameraServoCmd(Math.round(hh), Math.round(vv));
        ConnectedManager.getInstance().sendObject(cameraCmd, CmdConstant.CAMERA_SERVO);


        // https://github.com/androidthings/sample-simplepio/blob/master/java/pwm/src/main/java/com/example/androidthings/simplepio/PwmActivity.java
    }
}
