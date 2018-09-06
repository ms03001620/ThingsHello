package org.mark.thingshello.tensorflow;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.widget.TextView;

import org.mark.base.CameraUtils;

import java.io.IOException;

/**
 * Created by Mark on 2018/9/5
 */
public class Test {

    public static final String TAG = "Test";
    private ImageClassifierQuantizedMobileNet classifier;

    public Activity getActivity() {
        return null;
    }


    public Test(Context context) {

        try {
            // create either a new ImageClassifierQuantizedMobileNet or an ImageClassifierFloatInception
            classifier = new ImageClassifierQuantizedMobileNet(context);
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize an image classifier.", e);
        }
    }

    public void classifyFrame(byte[] bytes) {
        Bitmap bitmap = CameraUtils.createFromBytes(bytes);

        SpannableStringBuilder textToShow = new SpannableStringBuilder();
        //textureView.getBitmap(classifier.getImageSizeX(), classifier.getImageSizeY());
        classifier.classifyFrame(bitmap, textToShow);
        bitmap.recycle();
        showToast(textToShow);
    }

    public void classifyFrame(Bitmap bitmap) {
        SpannableStringBuilder textToShow = new SpannableStringBuilder();
        //textureView.getBitmap(classifier.getImageSizeX(), classifier.getImageSizeY());
        classifier.classifyFrame(bitmap, textToShow);
        bitmap.recycle();
        showToast(textToShow);
    }


    public void onDestroy() {
        classifier.close();
    }


    private void showToast(final SpannableStringBuilder builder) {
        Log.d("______", "result;"+builder.toString());

    }
}
