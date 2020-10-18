package org.mark.mobile;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.gcssloop.widget.RockerView;

import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.mobile.ctrl.RockerListener;

import androidx.appcompat.app.AppCompatActivity;

public class TestActivity extends AppCompatActivity {
    TextView mTextView;

    private int mSpeedCurrent = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        mTextView = findViewById(R.id.text);
        RockerView mRockerView = findViewById(R.id.rocker);

        mRockerView.setListener(m);

    }

    RockerListener m = new RockerListener() {
        @Override
        public void onEvent(int angle, float power) {

            WheelCmd direction = new WheelCmd(roundSpeed(angle, Math.round(mSpeedCurrent * power)));
            Log.d("Wheel", "angle:" + angle + ", power:" + power + ", " + direction.toString());

            mTextView.setText(direction.getLeft() + ", " + direction.getRight());
        }
    };


}
