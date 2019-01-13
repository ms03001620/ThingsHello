package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;

public class PreviewActivity extends AppCompatActivity {
    PreviewPresenter mPresent;
    private ImageView mPreview;
    private TextView mTextInfo;
    private TextView mTextBytes;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        mPresent = new PreviewPresenter(this);
        mPreview = findViewById(R.id.image);
        mTextInfo = findViewById(R.id.text_info);
        mTextBytes = findViewById(R.id.text_bytes);
        initServo();
    }


    public void updateImage(final Bitmap bitmap, final String sizeString) {
        mPreview.post(new Runnable() {
            @Override
            public void run() {
                mPreview.setImageBitmap(bitmap);
                mTextBytes.setText(sizeString);
            }
        });
    }

    public void updateInfo(final String info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextInfo.setText(info);
            }
        });
    }

    @Override
    protected void onStart() {
        mPresent.onStart();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mPresent.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mPresent.release();
        super.onDestroy();
    }


    int h;
    int v;

    private void initServo() {
        findViewById(R.id.btn_up).setOnClickListener(servoListener);
        findViewById(R.id.btn_down).setOnClickListener(servoListener);
        findViewById(R.id.btn_left).setOnClickListener(servoListener);
        findViewById(R.id.btn_right).setOnClickListener(servoListener);
    }

    private View.OnClickListener servoListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_up:
                    v++;
                    break;
                case R.id.btn_down:
                    v--;
                    break;
                case R.id.btn_left:
                    h--;
                    break;
                case R.id.btn_right:
                    h++;
                    break;
                case R.id.btn_stop:
                    CameraServoCmd cameraCmd = new CameraServoCmd(0, 0);
                    ConnectedManager.getInstance().sendObject(cameraCmd, CmdConstant.CAMERA_SERVO);
                    return;
            }

            float vv = 2.5f + 10 * v/180.0f;
            float hh = 2.5f + 10 * h/180.0f;

            CameraServoCmd cameraCmd = new CameraServoCmd(Math.round(hh), Math.round(vv));
            ConnectedManager.getInstance().sendObject(cameraCmd, CmdConstant.CAMERA_SERVO);

           // https://github.com/androidthings/sample-simplepio/blob/master/java/pwm/src/main/java/com/example/androidthings/simplepio/PwmActivity.java
        }
    };
}
