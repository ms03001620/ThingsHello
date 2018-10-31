package org.mark.prework;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.base.thread.MultiThread;
import org.mark.lib_tensorflow.Classifier;
import org.mark.lib_tensorflow.TensorFlowImageClassifier;
import org.mark.prework.db.DbMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Mark on 2018/10/24
 */
public class CheckPresent {
    private CheckActivity checkActivity;
    private List<Classifier> classifierList;
    private List<String> images;

    public CheckPresent(CheckActivity checkActivity) {
        this.checkActivity = checkActivity;
    }

    public void initModule(TfFileUtils.ModelFolderInfo info, File file) throws Exception {
        classifierList = new ArrayList<>();
        classifierList.add(TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224));
        classifierList.add(TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224));
        classifierList.add(TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224));
        classifierList.add(TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), 224));


        String labelString = ((TensorFlowImageClassifier) classifierList.get(0)).getLabelStrings();

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
        checkActivity.showProcessDialog(images.size());

        final List<TfFileUtils.ImageAcc> accs = new CopyOnWriteArrayList<>();

        MultiThread.multiThreadProcess(images, classifierList.size(), new MultiThread.ICallback<List<String>>() {
            @Override
            public void onThreadProcess(List<String> data, int threadIndex) {
                for (String path : data) {
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    TfFileUtils.ImageAcc acc;

                    Classifier classifier = classifierList.get(threadIndex);
                    acc = calcImage(bitmap, path, classifier);

                    if (acc != null) {
                        accs.add(acc);
                    }
                    checkActivity.updateProcessDialog(1);
                }
            }

            @Override
            public void onAllThreadFinished(long spendMs) {
                checkActivity.addLogs("task size:" + images.size() + ", finished size:" + accs.size() + " spend:" + spendMs / 1000 + "s");
                checkActivity.hideProcessDialog();

                DbMock.getInstance().updateImages(accs);
                if (accs.size() > 0) {
                    checkActivity.enableToGridButton();
                }
            }
        });
    }

    public void loadRecentData() {
        String modelPath = DbMock.getInstance().loadRecentModelPath();
        if (!TextUtils.isEmpty(modelPath)) {
            checkActivity.showModelInfo(new File(modelPath));
        }
    }

    private TfFileUtils.ImageAcc calcImage(Bitmap bitmap, String path, Classifier classifier) {
        if (bitmap == null) {
            File file = new File(path);
            boolean deleted = file.delete();
            Log.e("CheckPresent", "file error delete:" + path + ", " + deleted);
            return null;
        }

        try {
            Bitmap tmp = CameraUtils.zoomImage(bitmap, classifier.getWidth(), classifier.getHeight());

            List<Classifier.Recognition> o = classifier.recognizeImage(tmp);

            if (o.size() == 0) {
                Log.e("CheckPresent", "classifier result error");
                return calcImage(bitmap, path, classifier);
            }

            TfFileUtils.ImageAcc acc = new TfFileUtils.ImageAcc(path, o);
            return acc;
        } catch (Exception e) {
            Log.e("CheckPresent", "recognizeImage", e);
        }

        return null;
    }

    public boolean isNoFileToPredict() {
        return images == null || images.size() == 0;
    }
}
