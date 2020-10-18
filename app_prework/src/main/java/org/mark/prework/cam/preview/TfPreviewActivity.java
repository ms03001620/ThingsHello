package org.mark.prework.cam.preview;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import org.mark.base.thread.WorkThreadHandler;
import org.mark.camera.ConfigFactory;
import org.mark.camera.DoorbellCamera;
import org.mark.lib_tensorflow.Classifier;
import org.mark.lib_tensorflow.TensorFlowImageClassifier;
import org.mark.prework.R;
import org.mark.prework.TfFileUtils;
import org.mark.prework.cam.ConfigData;

import java.io.File;
import java.io.IOException;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class TfPreviewActivity extends AppCompatActivity {

    private AutoFitTextureView mTextureView;
    ConfigFactory config;
    private TextView mTextView;

    Classifier classifier;

    WorkThreadHandler workThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tf_preview);

        mTextView = findViewById(R.id.text);
        mTextureView = findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(surfaceTextureListener);

        workThreadHandler = new WorkThreadHandler();


    }

    public Runnable mClassifyRunnable = new Runnable() {
        @Override
        public void run() {
            classifyFrame();
        }
    };

    private void classifyFrame() {
        if (classifier == null || !mTextureView.isAvailable()) {
            showToast("Uninitialized Classifier or invalid context.");
            return;
        }
        Bitmap bitmap =
                mTextureView.getBitmap(config.getWidth(), config.getHeight());

        if (bitmap == null) {
            Log.d("TfPreviewActivity", "bitmap null");
            return;
        }

        List<Classifier.Recognition> lists = classifier.recognizeImage(bitmap);
        StringBuilder textToShow = new StringBuilder();
        for (Classifier.Recognition recognition : lists) {
            textToShow.append(recognition.toString());
            textToShow.append("\n");
        }

        bitmap.recycle();
        showToast(textToShow.toString());


    }

    private void showToast(final String text) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(text);
                    }
                });
    }

    private boolean processIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            try {
                ConfigData configData = (ConfigData) intent.getSerializableExtra("config");
                config = new ConfigFactory(configData.getWidth(), configData.getHeight(), mTextureView);

                TfFileUtils.ModelFolderInfo info = TfFileUtils.checkModelFolder(new File(configData.getModelPath()));
                classifier = TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), configData.getWidth());
                mTextureView.setAspectRatio(configData.getWidth(), configData.getHeight());
            } catch (Exception e) {
                Log.d("TfPreviewActivity", "processIntent", e);
                return false;
            }
            return true;
        }

        return false;
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    if (!processIntent()) {
                        Toast.makeText(getApplicationContext(), "Intent need \'config extra\' data", Toast.LENGTH_LONG).show();
                        finish();
                    }


                    DoorbellCamera.getInstance().initializeCamera(getApplicationContext(), config);
                    workThreadHandler.runWorkThreadLoop(mClassifyRunnable);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    DoorbellCamera.getInstance().shutDown();
                    workThreadHandler.release();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };
}
