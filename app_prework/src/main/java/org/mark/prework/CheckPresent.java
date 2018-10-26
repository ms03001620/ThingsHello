package org.mark.prework;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.lib_tensorflow.Classifier;
import org.mark.lib_tensorflow.TensorFlowImageClassifier;
import org.mark.prework.db.DbMock;


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
        String labelString = ((TensorFlowImageClassifier) classifier).getLabelStrings();

        DbMock.getInstance().setLabelString(labelString);
        checkActivity.addLogs(labelString);
    }

    public void initImages(File pathFile) {
        String folder = TfFileUtils.getParentFolderName(pathFile);
        DbMock.getInstance().setFolderName(folder);

        this.images = new ArrayList<>(TfFileUtils.getPhotoList(pathFile));
        checkActivity.addLogs(folder + ", size:" + images.size());
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
        checkActivity.clearLogs();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<TfFileUtils.ImageAcc> accs = getImageAccs(images);
                DbMock.getInstance().updateImages(accs);

                for (TfFileUtils.ImageAcc acc : accs) {
                    checkActivity.addLogs(acc.toString());
                }

                if (accs.size() > 0) {
                    checkActivity.enableToGridButton();
                }
            }
        }).start();
    }

    private List<TfFileUtils.ImageAcc> getImageAccs(List<String> images) {
        List<TfFileUtils.ImageAcc> result = new ArrayList<>();

        //images = images.subList(0, 20);

        for (String path : images) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);

            if (bitmap == null) {
                File file = new File(path);
                boolean deleted = file.delete();
                Log.e("CheckPresent", "file error delete:" + path + ", " + deleted);
                continue;
            }

            try {
                bitmap = CameraUtils.zoomImage(bitmap, classifier.getWidth(), classifier.getHeight());

                List<Classifier.Recognition> o = classifier.recognizeImage(bitmap);
                TfFileUtils.ImageAcc acc = new TfFileUtils.ImageAcc(path, o);
                 result.add(acc);

            } catch (Exception e) {
                Log.e("CheckPresent", "recognizeImage", e);
            }
        }

        return result;
    }
}
