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
    private ImageClassifierFactory factory;

    public Test(Context context) {

        try {
            factory = new ImageClassifierFactory();
            factory.init(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize an image classifier.", e);
        }
    }

    public void classifyFrame(final Bitmap bitmap, final ConnectSelector connectSelector) {
        if (factory == null) {
            return;
        }
        BlockProcess.getInstance().run(new Runnable() {
            @Override
            public void run() {
                String label;
                try {
                    label = factory.feed(bitmap);
                } catch (Exception e) {
                    label = e.toString();
                }
                connectSelector.sendText(label);
                Log.d(TAG, "result \n" + label);
            }
        });
    }

    public void onDestroy() {
        if (factory == null) {
            return;
        }
        factory.close();
    }

}
