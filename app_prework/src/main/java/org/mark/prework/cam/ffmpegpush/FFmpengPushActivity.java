package org.mark.prework.cam.ffmpegpush;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import org.mark.ffmpeg.FFUtils;
import org.mark.prework.R;
import org.mark.prework.cam.PermissionActivity;
import org.mark.prework.cam.preview.AutoFitTextureView;


public class FFmpengPushActivity extends PermissionActivity {
    private AutoFitTextureView mTextureView;
    private TextView mTextView;
    private TextView mTextRight;

    FFmpegPushPresenter mFFmpegPushPresenter;

    int w = 640;
    int h = 480;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpegpush);
        mFFmpegPushPresenter = new FFmpegPushPresenter(this);

        mTextView = findViewById(R.id.text);
        mTextRight = findViewById(R.id.textRight);
        mTextureView = findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        System.out.println(FFUtils.avCodecInfo());
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.i("FFmpengPushActivity", "onSurfaceTextureAvailable "+"texture width = " + width + ", height = " + height);

            checkPermissionCamera(new CheckPermissionCameraCallback() {
                @Override
                public void onRejected() {
                    Toast.makeText(getApplicationContext(), "Video need camera permission..", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onAccept() {
                    mTextureView.setAspectRatio(w, h);
                    mFFmpegPushPresenter.start(getApplicationContext(), w, h);
                }
            });

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.i("FFmpengPushActivity", "onSurfaceTextureSizeChanged "+"texture width = " + width + ", height = " + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.i("FFmpengPushActivity", "onSurfaceTextureAvailable");
            mFFmpegPushPresenter.stop();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            Log.i("FFmpengPushActivity", "onSurfaceTextureDestroyed");
        }
    };

    public Bitmap findBitmapFormView() {
        return mTextureView.getBitmap(w, h);
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

}
