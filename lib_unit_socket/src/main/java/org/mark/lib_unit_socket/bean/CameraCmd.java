package org.mark.lib_unit_socket.bean;

/**
 * Created by Mark on 2018/11/20
 */
public class CameraCmd {
    private int action;

    public CameraCmd(int action) {
        this.action = action;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public boolean isOpenAction() {
        return action == 1;
    }
}
