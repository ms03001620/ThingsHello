package org.mark.prework;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
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
    private CheckActivity checkActivity;
    private Classifier classifier;
    private List<String> images;

    public CheckPresent(CheckActivity checkActivity) {
        this.checkActivity = checkActivity;
    }

    public void initModule(TfFileUtils.ModelFolderInfo info, File file) throws Exception {
        classifier = TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224);
        String labelString = ((TensorFlowImageClassifier) classifier).getLabelStrings();

        DbMock.getInstance().setLabelString(labelString);
        DbMock.getInstance().saveRecentModelPath(file.getPath());
        checkActivity.addLogs(labelString);
    }

    public void initImages(File pathFile) {
        String folder = TfFileUtils.getParentFolderName(pathFile);
        DbMock.getInstance().setFolderName(folder);

        this.images = new ArrayList<>(TfFileUtils.getPhotoList(pathFile));
        checkActivity.addLogs(folder + ", size:" + images.size());
    }

    public void doPredictAll() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                List<TfFileUtils.ImageAcc> accs = getImageAccs(images);
                long end = System.currentTimeMillis();

                checkActivity.addLogs("task size:" + images.size() + ", finished size:" + accs.size() + " spend:" + (end - start) / 1000 + "s");
                DbMock.getInstance().updateImages(accs);

                if (accs.size() > 0) {
                    checkActivity.enableToGridButton();
                }
            }
        }).start();
    }

    public void loadRecentData() {
        String modelPath = DbMock.getInstance().loadRecentModelPath();
        if (!TextUtils.isEmpty(modelPath)) {
            checkActivity.showModelInfo(new File(modelPath));
        }
    }

    private List<TfFileUtils.ImageAcc> getImageAccs(List<String> images) {
        //images = images.subList(0, 100);
        checkActivity.showProcessDialog(images.size());
        List<TfFileUtils.ImageAcc> result = new ArrayList<>();
        for (int i=0;i<images.size();i++) {
            checkActivity.updateProcessDialog(i);
            String path = images.get(i);
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
                bitmap.recycle();
                TfFileUtils.ImageAcc acc = new TfFileUtils.ImageAcc(path, o);
                result.add(acc);

                bitmap.recycle();
            } catch (Exception e) {
                Log.e("CheckPresent", "recognizeImage", e);
            }
        }
        checkActivity.hideProcessDialog();
        return result;
    }

    public boolean isNoFileToPredict() {
        return images == null || images.size() == 0;
    }
}
