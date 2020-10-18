package org.mark.prework.cam.preview;

import android.content.Context;
import android.graphics.Bitmap;

import org.mark.lib_tensorflow.Classifier;
import org.mark.prework.ml.MobilenetV1025160Quantized1Metadata1;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by mark on 2020/10/18
 */

public class ModelWithMetaClassifier implements Classifier {
    MobilenetV1025160Quantized1Metadata1 model;

    public ModelWithMetaClassifier(Context context) throws Exception {
        model = MobilenetV1025160Quantized1Metadata1.newInstance(context);
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        List<Recognition> result = new ArrayList<>();
        try {
            // Creates inputs for reference.
            TensorImage image = TensorImage.fromBitmap(bitmap);

            // Runs model inference and gets result.
            MobilenetV1025160Quantized1Metadata1.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();

            Collections.sort(probability, new Comparator<Category>() {
                @Override
                public int compare(Category a, Category b) {
                    if (a.getScore() > b.getScore()) {
                        return -1;
                    } else if (a.getScore() < b.getScore()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });

            int num = Math.min(5, probability.size());
            for (int i = 0; i < num; i++) {
                Category c = probability.get(i);
                Recognition r = convert(c);
                result.add(r);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private Recognition convert(Category c) {
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
