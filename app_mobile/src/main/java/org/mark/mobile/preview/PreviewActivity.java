package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import org.mark.mobile.R;

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
        super.onStart();
        mPresent.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPresent.onStop();
    }

    @Override
    protected void onDestroy() {
        mPresent.release();
        super.onDestroy();
    }
}
