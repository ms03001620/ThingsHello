package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;

public class PreviewActivity extends AppCompatActivity {
    PreviewPresenter mPresent;
    private ImageView mPreview;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        mPresent = new PreviewPresenter(this);
        mPreview = findViewById(R.id.image);
    }


    public void updateImage(final Bitmap bitmap){
        mPreview.post(new Runnable() {
            @Override
            public void run() {
                mPreview.setImageBitmap(bitmap);
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
