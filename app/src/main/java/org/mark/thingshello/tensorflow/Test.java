package org.mark.thingshello.tensorflow;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.base.executor.BlockProcess;
import org.mark.thingshello.video.sender.ConnectSelector;

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
    }

    public void classifyFrame(final Bitmap bitmap, final ConnectSelector connectSelector) {
        BlockProcess.getInstance().run(new Runnable() {
            @Override
            public void run() {
                SpannableStringBuilder textToShow = new SpannableStringBuilder();
                //textureView.getBitmap(classifier.getImageSizeX(), classifier.getImageSizeY());
                classifier.classifyFrame(bitmap, textToShow);
                // bitmap.recycle();

                String label = textToShow.toString();
                connectSelector.sendText(label);

                Log.d(TAG, "result \n"+label);
            }
        });
    }


    public void onDestroy() {
        classifier.close();
    }

}
