package org.mark.mobile.ctrl;

/** 摇杆角度 → 左右轮差速。 */
public final class SpeedMixer {
    private SpeedMixer() {
    }

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
