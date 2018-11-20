package org.mark.thingshello.ctrl.wheel;


public interface IWheelAction {
    void forward();

    void back();

    void forward(int speedLeft, int speedRight);

    void stop();

    void left();

    void right();

    void rotateLeft();

    void rotateRight();

    void release();
}
