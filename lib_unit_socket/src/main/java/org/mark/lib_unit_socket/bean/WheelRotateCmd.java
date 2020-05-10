package org.mark.lib_unit_socket.bean;

/**
 * Created by Mark on 2018/11/20
 */
public class WheelRotateCmd {
    public final static int DEFAULT_SPEED = 40;

    public enum Rotate{
        LEFT,RIGHT
    }

    Rotate rotate;
    int speed = DEFAULT_SPEED;

    public WheelRotateCmd(){
        rotate = Rotate.LEFT;
    }

    public WheelRotateCmd(Rotate rotate, int speed) {
        this.rotate = rotate;
        this.speed = speed;
    }

    public Rotate getRotate() {
        return rotate;
    }

    public void setRotate(Rotate rotate) {
        this.rotate = rotate;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    @Override
    public String toString() {
        return "WheelRotateCmd{" +
                "leftOrRight=" + rotate +
                ", speed=" + speed +
                '}';
    }
}
