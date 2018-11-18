package org.mark.mobile;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.gcssloop.widget.RockerView;

import org.mark.base.CommandConstant;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ctrl.RockerListener;
import org.mark.mobile.ctrl.SimpleRockerListener;

public class TestActivity extends AppCompatActivity {

    TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        mTextView = findViewById(R.id.text);
        RockerView mRockerView = findViewById(R.id.rocker);

        mRockerView.setListener(m);

    }

    RockerListener m = new RockerListener(){
        @Override
        public void callback(int eventType, int currentAngle, float currentDistance) {
            if (eventType == RockerView.EVENT_CLOCK) {
                return;
            }


            Log.d("callback","angle:"+currentAngle+", distance:"+currentDistance);

            int[] value = roundSpeed(currentAngle, 100);

            mTextView.setText(value[0]+", "+value[1]);

        }
    };



    public int[] roundSpeed(int angle, int max) {
        int[] result = new int[2];
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

    private int value(float p, int angle) {
        return (int) (angle * p);
    }

}
