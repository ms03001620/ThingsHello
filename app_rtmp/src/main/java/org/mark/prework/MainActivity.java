package org.mark.prework;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

import org.mark.prework.component.AutoFitTextureView;
import org.mark.prework.component.ConfigData;


public class MainActivity extends PermissionActivity {
    AutoFitTextureView mTextureView;
    MainPresenter mFFmpegPushPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFFmpegPushPresenter = new MainPresenter(this);
        mTextureView = findViewById(R.id.texture);

        checkPermissionCamera(new CheckPermissionCameraCallback() {
            @Override
            public void onRejected() {
                onMessageShow("need camera permission");
            }

            @Override
            public void onAccept() {
                mTextureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        });
    }


    public void onMessageShow(String message) {
        ((TextView)findViewById(R.id.text)).setText(message);
        //Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            onMessageShow("record start");
            try {
                ConfigData configData = new ConfigData();
                configData.setWidth(640);
                configData.setHeight(480);

                mTextureView.setAspectRatio(configData.getWidth(), configData.getHeight());
                mFFmpegPushPresenter.start(getApplicationContext(), configData);
            } catch (Exception e) {
                Log.d("MainActivity", "processIntent", e);
                finish();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            onMessageShow("record stop");
            mFFmpegPushPresenter.stop();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };



}
