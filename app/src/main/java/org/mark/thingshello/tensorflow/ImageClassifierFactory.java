package org.mark.thingshello.tensorflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;

import org.mark.lib_tensorflow.Classifier;
import org.mark.lib_tensorflow.ImageClassifierFloatInception;
import org.mark.lib_tensorflow.ImageClassifierQuantizedMobileNet;
import org.mark.lib_tensorflow.TensorFlowImageClassifier;

/**
 * Created by Mark on 2018/10/22
 */
public class ImageClassifierFactory {

    Classifier c1;

    ImageClassifierQuantizedMobileNet c2;

    ImageClassifierFloatInception c3;


    public void init(Context context) throws Exception {

        c1 = TensorFlowImageClassifier.create(context.getAssets(), "optimized_graph_arrow.lite","retrained_labels_arrow.txt", 224);

        // c2 = new ImageClassifierQuantizedMobileNet(context);

        //c3 = new ImageClassifierFloatInception(context);

    }

    public String feed(Bitmap bitmap) {
        if (c1 != null) {
            Object o = c1.recognizeImage(bitmap);
            return o.toString();
        }


        if (c2 != null) {
            SpannableStringBuilder textToShow = new SpannableStringBuilder();
            c2.classifyFrame(bitmap, textToShow);
            return textToShow.toString();
        }

        if (c3 != null) {
            SpannableStringBuilder textToShow = new SpannableStringBuilder();
            c3.classifyFrame(bitmap, textToShow);
            return textToShow.toString();
        }

        throw new IllegalArgumentException("init error");
    }

    public void close() {

        if (c1 != null) {
            c1.close();
        }

        if (c2 != null) {
            c2.close();
        }

        if (c3 != null) {
            c3.close();
        }
    }


    public int getWidth() {
        if (c1 != null) {
            return c1.getWidth();
        }

        if (c2 != null) {
            return c2.getImageSizeX();
        }

        if (c3 != null) {
            return c3.getImageSizeX();
        }

        return 0;
    }

    public int getHeight() {
        if (c1 != null) {
            return c1.getHeight();
        }

        if (c2 != null) {
            return c2.getImageSizeY();
        }

        if (c3 != null) {
            return c3.getImageSizeY();
        }

        return 0;
    }
}
