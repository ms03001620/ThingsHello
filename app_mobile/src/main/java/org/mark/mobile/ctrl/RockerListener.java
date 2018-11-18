package org.mark.mobile.ctrl;

import android.support.annotation.NonNull;
import android.util.Log;

import com.gcssloop.widget.RockerView;

/**
 * Created by mark on 2018/7/28
 */
public class RockerListener implements RockerView.RockerListener {
    private static final String TAG = "SimpleRockerListener";

    public enum Action {
        STOP,
        UP,
        RIGHT,
        DOWN,
        LEFT
    }

    public RockerListener() {
    }

    @Override
    public void callback(int eventType, int currentAngle, float currentDistance) {
        if (eventType == RockerView.EVENT_CLOCK) {
            return;
        }


        Log.d("callback", "angle:" + currentAngle + ", distance:" + currentDistance);

        //90  A100 B100
        //0   A100 B0
        //180 A0   B100

    }


    public static int[] roundSpeed(int angle, int max) {
        int[] result = new int[2];
        if (angle < 0) {
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
