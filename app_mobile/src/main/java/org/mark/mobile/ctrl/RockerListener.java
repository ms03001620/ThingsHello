package org.mark.mobile.ctrl;

import com.gcssloop.widget.RockerView;


public abstract class RockerListener implements RockerView.RockerListener {
    private final static int MAX_DIST = 400;

    @Override
    public void callback(int eventType, int currentAngle, float currentDistance) {
        if (eventType == RockerView.EVENT_CLOCK) {
            return;
        }
        currentDistance = currentDistance > MAX_DIST ? MAX_DIST : currentDistance;
        onEvent(currentAngle, currentDistance * (1.0f / MAX_DIST));
    }

    /**
     * 控制杆事件
     * @param currentAngle 角度（1-360）
     * @param power 能量(0-1.0);
     */
    public abstract void onEvent(int currentAngle, float power);


    /**
     *
     * 根据目标角度算出左右两轮的速度差异，例如在右转角度下右轮速度要小于左轮
     *
     * 上90   L100, R 100
     * 下270  L-100, R-100
     * 左180  L0, R100
     * 右1    L100, 0
     *
     * @param angle 角度
     * @param max 最大速度
     */
    public static int[] roundSpeed(int angle, int max) {
        int[] result = new int[2];
        if (angle < 0 || max <= 0) {
            return result;
        }
        float p = max / 90.0f;

        if (angle <= 90) {
            result[0] = max;
            result[1] = value(p, angle);
        } else if (angle <= 180) {
            result[0] = max - value(p, angle - 90);
            result[1] = max;
        } else if (angle <= 270) {
            result[0] = max - value(p, angle - 90);
            result[1] = -max;
        } else if (angle <= 360) {
            result[1] = -(max - value(p, angle - 270));
            result[0] = -max;
        }

        return result;
    }

    private static int value(float p, int angle) {
        return (int) (angle * p);
    }


}
