package org.mark.lib_unit_socket.bean;

/**
 * Created by Mark on 2018/11/20
 */
public class WheelCmd {
    public final static int DEFAULT_SPEED = 10;

    public WheelCmd(int left, int right) {
        this.left = left;
        this.right = right;
    }

    private int left;
    private int right;

    public WheelCmd(int[] value) {
        left = value[0];
        right = value[1];
    }

    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    @Override
    public String toString() {
        return "WheelCmd{" +
                "left=" + left +
                ", right=" + right +
                '}';
    }
}
