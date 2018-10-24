package org.mark.check;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.mark.base.CameraUtils;
import org.mark.thingshello.tensorflow.Classifier;
import org.mark.thingshello.tensorflow.TensorFlowImageClassifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mark on 2018/10/24
 */
public class CheckPresent {
    CheckActivity checkActivity;

    Classifier classifier;

    List<String> images;

    public CheckPresent(CheckActivity checkActivity) {
        this.checkActivity = checkActivity;
    }

    public void initModule(TfFileUtils.ModelFolderInfo info) throws Exception {
        classifier = TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224);
    }

    public void initImages(File pathFile) {
        List<String> images = TfFileUtils.getPhotoList(pathFile);
        this.images = new ArrayList<>(images);
        checkActivity.addLogs("size:" + images.size());
    }

    public String doPredictRandom() {
        String resultString = "";

        String first = images.get(0);

        Bitmap bitmap = BitmapFactory.decodeFile(first);

        bitmap = CameraUtils.zoomImage(bitmap, classifier.getWidth(), classifier.getHeight());

        try {
            List<Classifier.Recognition> o = classifier.recognizeImage(bitmap);
            resultString = o.toString();
        } catch (Exception e) {
            resultString = e.toString();
        }

        return resultString;

    }

    public void doPredictAll() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<TfFileUtils.ImageAcc> accs = getImageAccs(images);

                for (TfFileUtils.ImageAcc acc : accs) {
                    checkActivity.addLogs(acc.toString());
                }
            }
        }).start();
    }

    private List<TfFileUtils.ImageAcc> getImageAccs(List<String> images) {
        List<TfFileUtils.ImageAcc> result = new ArrayList<>();

        for (String path : images) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);

            bitmap = CameraUtils.zoomImage(bitmap, classifier.getWidth(), classifier.getHeight());

            try {
                List<Classifier.Recognition> o = classifier.recognizeImage(bitmap);

                TfFileUtils.ImageAcc acc = new TfFileUtils.ImageAcc(path, o);

                result.add(acc);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
