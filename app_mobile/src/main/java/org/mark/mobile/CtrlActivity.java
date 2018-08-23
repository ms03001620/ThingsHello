package org.mark.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import com.gcssloop.widget.RockerView;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ctrl.CtrlPresent;
import org.mark.mobile.ctrl.SimpleRockerListener;
import org.mark.mobile.preview.PreviewActivity;

public class CtrlActivity extends AppCompatActivity {
    private static final String TAG = "CtrlActivity";
    RockerView mRockerView;
    EditText mEditMessage;

    CtrlPresent mPresent;

/*    String KEY_CLEAR = "$0,0,0,0,0,0,0,0,0#";
    String KEY_GO = "$1,0,0,0,0,0,0,0,0#";
    String KEY_BACK = "$2,0,0,0,0,0,0,0,0#";
    String KEY_RIGHT = "$3,0,0,0,0,0,0,0,0#";
    String KEY_LEFT = "$4,0,0,0,0,0,0,0,0#";
    String KEY_ROUND_LEFT = "$0,1,0,0,0,0,0,0,0#";
    String KEY_ROUND_RIGHT = "$0,2,0,0,0,0,0,0,0#";*/

    String KEY_CLEAR = "0";
    String KEY_GO = "1";
    String KEY_BACK = "2";
    String KEY_LEFT = "3";
    String KEY_RIGHT = "4";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ctrl);
        mPresent = new CtrlPresent(this);
        mEditMessage = findViewById(R.id.edit_message);
        mEditMessage.setMovementMethod(ScrollingMovementMethod.getInstance());

        final EditText edit = findViewById(R.id.edit);
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = edit.getText().toString();
                ConnectedManager.getInstance().sendMessage(message);
            }
        });

        findViewById(R.id.btn_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CtrlActivity.this, PreviewActivity.class);
                startActivity(intent);
                finish();
            }
        });

        SeekBar seekBar = findViewById(R.id.seek_speed);
        seekBar.setMax(100);
        seekBar.setProgress(10);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ConnectedManager.getInstance().sendMessage(String.valueOf(i), 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mRockerView = findViewById(R.id.rocker);
        mRockerView.setListener(new SimpleRockerListener(new SimpleRockerListener.OnActionChange() {
            @Override
            public void onAction(SimpleRockerListener.Action action) {

                switch (action) {
                    case UP:
                        ConnectedManager.getInstance().sendMessage(KEY_GO);
                        break;
                    case RIGHT:
                        ConnectedManager.getInstance().sendMessage(KEY_RIGHT);
                        break;
                    case DOWN:
                        ConnectedManager.getInstance().sendMessage(KEY_BACK);
                        break;
                    case LEFT:
                        ConnectedManager.getInstance().sendMessage(KEY_LEFT);
                        break;
                    case STOP:
                        ConnectedManager.getInstance().sendMessage(KEY_CLEAR);
                        break;

                }
            }
        }));

        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
    }


    ClientMessageCallback mClientMessageCallback = new ClientMessageCallback() {
        @Override
        public void onReceiveMessage(final byte[] message, int type) {
            Log.d(TAG, "onReceiveMessage length" + message.length + ", type:" + type);
        }

        @Override
        public void onExceptionToReOpen(@NonNull Exception e) {

        }

        @Override
        public void onLogMessage(final String message, @Nullable Exception e) {
            Log.d(TAG, "onLogMessage length" + message);
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
