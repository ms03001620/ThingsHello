package org.mark.prework.cam.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

import org.mark.camera.ConfigFactory;
import org.mark.camera.DoorbellCamera;
import org.mark.prework.R;
import org.mark.prework.cam.ConfigData;


public class TfPreviewActivity extends Activity {
    private AutoFitTextureView mTextureView;
    ConfigFactory config;
    private TextView mTextView;
    private TextView mTextRight;

    TfPreviewPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tf_preview);
        mPresenter = new TfPreviewPresenter(this);

        mTextView = findViewById(R.id.text);
        mTextRight = findViewById(R.id.textRight);
        mTextureView = findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    public Bitmap findBitmapFormView() {
        return mTextureView.getBitmap(config.getWidth(), config.getHeight());
    }

    public void showTextRight(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextRight.setText(text);
            }
        });
    }

    public void showTextLeft(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setText(text);
            }
        });
    }


    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            try {
                Intent intent = getIntent();
                ConfigData configData = (ConfigData) intent.getSerializableExtra("config");
                mTextureView.setAspectRatio(configData.getWidth(), configData.getHeight());
                config = new ConfigFactory(configData.getWidth(), configData.getHeight(), mTextureView);
                DoorbellCamera.getInstance().initializeCamera(getApplicationContext(), config);
                mPresenter.startClassifier(getApplicationContext(), configData);
            } catch (Exception e) {
                Log.d("TfPreviewActivity", "processIntent", e);
                finish();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            mPresenter.stopClassifier();
            DoorbellCamera.getInstance().shutDown();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

}
