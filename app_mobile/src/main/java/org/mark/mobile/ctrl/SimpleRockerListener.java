package org.mark.mobile.ctrl;

import android.support.annotation.NonNull;
import android.util.Log;

import com.gcssloop.widget.RockerView;

/**
 * Created by mark on 2018/7/28
 */
public class SimpleRockerListener implements RockerView.RockerListener {
    private static final String TAG = "SimpleRockerListener";

    public interface OnActionChange {
        void onAction(Action action);
    }

    public enum Action {
        STOP,
        UP,
        RIGHT,
        DOWN,
        LEFT
    }

    private final static int OFFSET = 20;
    private Action mAction = Action.STOP;
    private OnActionChange mOnActionChange;

    public SimpleRockerListener(@NonNull OnActionChange listener) {
        mOnActionChange = listener;
    }

    @Override
    public void callback(int eventType, int currentAngle, float currentDistance) {
        if (eventType == RockerView.EVENT_CLOCK) {
            return;
        }

        Action action = Action.STOP;

        if (currentAngle == -1) {
            action = Action.STOP;
            // Log.d(TAG, "Action.STOP");
        } else if (currentAngle < OFFSET || currentAngle > (360 - OFFSET)) {
            action = Action.RIGHT;
            //Log.d(TAG, "Action.RIGHT");
        } else if ((Math.abs(currentAngle - 270) < OFFSET)) {
            action = Action.DOWN;
            //Log.d(TAG, "Action.DOWN");
        } else if ((Math.abs(currentAngle - 180) < OFFSET)) {
            action = Action.LEFT;
            //Log.d(TAG, "Action.LEFT");
        } else if ((Math.abs(currentAngle - 90) < OFFSET)) {
            action = Action.UP;
            //Log.d(TAG, "Action.UP");
        }

        // stop 移动距离可以过小。其他的必须有一定的距离才是有效的
        if (action != Action.STOP && currentDistance < 150) {
            // 移动距离太小
            return;
        }

        onActionChanged(action);
    }

    public void onActionChanged(@NonNull Action action) {
        if (mAction != action) {
            mAction = action;
            mOnActionChange.onAction(mAction);
            Log.d(TAG, "onActionChanged:" + mAction.name());
        }
    }
}
