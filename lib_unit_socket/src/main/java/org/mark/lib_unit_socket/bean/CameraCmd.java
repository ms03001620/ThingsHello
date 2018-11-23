package org.mark.lib_unit_socket.bean;

/**
 * Created by Mark on 2018/11/20
 */
public class CameraCmd {
    private int action;
    private int width;
    private int height;
    private tfCmd tfCmd;
    private boolean transferVideo;

    public CameraCmd(int action, int width, int height) {
        this.action = action;
        this.width = width;
        this.height = height;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public tfCmd getTfCmd() {
        return tfCmd;
    }

    public void setTfCmd(tfCmd tfCmd) {
        this.tfCmd = tfCmd;
    }

    public boolean isOpenAction() {
        return action == 1;
    }

    public boolean isCloseAction() {
        return action == 0;
    }

    public boolean isTfEnable() {
        return tfCmd != null;
    }

    public boolean isTransferVideo() {
        return transferVideo;
    }

    public void setTransferVideo(boolean transferVideo) {
        this.transferVideo = transferVideo;
    }

    @Override
    public String toString() {
        return "CameraCmd{" +
                "action=" + action +
                ", width=" + width +
                ", height=" + height +
                ", tfCmd=" + tfCmd +
                ", transferVideo=" + transferVideo +
                '}';
    }
}
