package org.mark.mobile;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import com.gcssloop.widget.RockerView;

import org.mark.base.PreferUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.WheelCmd;
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

        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
    }


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
