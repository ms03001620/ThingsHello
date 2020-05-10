package org.mark.mobile;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import com.gcssloop.widget.RockerView;

import org.mark.base.PreferUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.lib_unit_socket.bean.WheelRotateCmd;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ctrl.KeyIndex;
import org.mark.mobile.ctrl.RockerListener;
import org.mark.mobile.preview.EyesFragment;

public class CtrlActivity extends AppCompatActivity {
    private static final String TAG = "CtrlActivity";

    private int mSpeedCurrent = WheelCmd.DEFAULT_SPEED;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ctrl);
        findViewById(R.id.btn_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new EyesFragment()).commit();
            }
        });

        SeekBar seekBar = findViewById(R.id.seek_speed);
        seekBar.setMax(100);
        seekBar.setProgress(WheelCmd.DEFAULT_SPEED);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mSpeedCurrent = i;
                PreferUtils.getInstance().put(KeyIndex.SPEED, mSpeedCurrent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


        RockerView mRockerView = findViewById(R.id.rocker);
        mRockerView.setListener(new RockerListener() {
            @Override
            public void onEvent(int angle, float power) {
                WheelCmd direction = new WheelCmd(roundSpeed(angle, Math.round(mSpeedCurrent * power)));
                Log.d("Wheel", "angle:" + angle + ", power:" + power + ", " + direction.toString());
                ConnectedManager.getInstance().sendObject(direction, CmdConstant.WHEEL);
            }
        });

        // 原地左转
        findViewById(R.id.btnLeft).setOnTouchListener(rotateTouchListener);
        // 原地右转
        findViewById(R.id.btnRight).setOnTouchListener(rotateTouchListener);

        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
    }

    View.OnTouchListener rotateTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            WheelRotateCmd rotateCmd = new WheelRotateCmd();
            switch (v.getId()) {
                case R.id.btnLeft:
                    rotateCmd.setRotate(WheelRotateCmd.Rotate.LEFT);
                    break;
                case R.id.btnRight:
                    rotateCmd.setRotate(WheelRotateCmd.Rotate.RIGHT);
                    break;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_DOWN:
                    rotateCmd.setSpeed(mSpeedCurrent);
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                default:
                    rotateCmd.setSpeed(0); //stop
                    break;
            }

            Log.d("WheelRotateCmd", "cmd:" + rotateCmd.toString() + ",a:" + event.getAction());
            ConnectedManager.getInstance().sendObject(rotateCmd, CmdConstant.WHEEL_ROTATE);
            return false;
        }
    };


    ClientMessageCallback mClientMessageCallback = new ClientMessageCallback() {
        @Override
        public void onReceiveMessage(final byte[] message, int type) {
        }

        @Override
        public void onExceptionToReOpen(@NonNull Exception e) {

        }

        @Override
        public void onLogMessage(final String message, @Nullable Exception e) {
        }

        @Override
        public void onStatusChange(@NonNull Status status) {
            if (status == Status.NO_CONNECT) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CtrlActivity.this, "No connect", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        }
    };


    @Override
    protected void onDestroy() {
        ConnectedManager.getInstance().removeCallback(mClientMessageCallback);
        super.onDestroy();
    }
}
