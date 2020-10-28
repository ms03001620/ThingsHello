package org.mark.prework.cam.preview;

import android.content.Context;
import android.graphics.Bitmap;

import org.mark.lib_tensorflow.Classifier;
import org.mark.prework.ml.MobilenetV1025160Quantized1Metadata1;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.util.List;

/**
 * Created by mark on 2020/10/18
 */

public class ModelWithMetaClassifier implements Classifier {
    MobilenetV1025160Quantized1Metadata1 model;

    public ModelWithMetaClassifier(Context context) throws Exception {
        model = MobilenetV1025160Quantized1Metadata1.newInstance(context);
    }

    public List<Category> getCategory(Bitmap bitmap){
        // Creates inputs for reference.
        TensorImage image = TensorImage.fromBitmap(bitmap);
        // Runs model inference and gets result.
        MobilenetV1025160Quantized1Metadata1.Outputs outputs = model.process(image);
        List<Category> probability = outputs.getProbabilityAsCategoryList();
        return probability;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        throw new UnsupportedOperationException("please call #getCategory");
    }

    public static Recognition convert(Category c) {
        int id = c.getLabel().hashCode();
        String title = c.getLabel();
        float confidence = c.getScore();

        Recognition r = new Recognition(id, title, confidence);
        return r;
    }

    @Override
    public void close() {
        if (model != null) {
            model.close();
        }
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }
}
