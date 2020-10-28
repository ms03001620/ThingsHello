package org.mark.prework.cam.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.mark.base.thread.WorkThreadHandler;
import org.mark.lib_tensorflow.Classifier;
import org.mark.prework.cam.ConfigData;
import org.mark.prework.cam.preview.category.DisplayStrategy;
import org.mark.prework.cam.preview.category.MemorySpline;
import org.mark.prework.cam.preview.category.SortLimitList;
import org.tensorflow.lite.support.label.Category;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by mark on 2020/10/28
 */

class TfPreviewPresenter {
    WeakReference<TfPreviewActivity> weakReference;
    WorkThreadHandler workThreadHandler;
    Classifier classifier;
    SecondsCountUtils secondsCountUtils;
    DisplayStrategy displayStrategy;
    DisplayStrategy displayStrategyWeight;

    public TfPreviewPresenter(TfPreviewActivity activity) {
        weakReference = new WeakReference<>(activity);
        workThreadHandler = new WorkThreadHandler();

        secondsCountUtils = new SecondsCountUtils();
        displayStrategy = new DisplayStrategy();
        displayStrategyWeight = new DisplayStrategy(new SortLimitList(), new MemorySpline());
    }

    public Runnable mClassifyRunnable = new Runnable() {
        @Override
        public void run() {
            classifyFrame();
        }
    };

    private void classifyFrame() {
        Bitmap bitmap = findBitmapFormView();
        if (bitmap == null) {
            Log.d("TfPreviewActivity", "bitmap null");
            return;
        }

        ModelWithMetaClassifier mclassifier = (ModelWithMetaClassifier) classifier;

        long start = System.currentTimeMillis();
        List<Category> categories = mclassifier.getCategory(bitmap);
        Log.d("classifyFrame", "pass:" + (System.currentTimeMillis() - start));

        displayStrategy.process(categories, new DisplayStrategy.INotifyDisplay() {
            @Override
            public void notify(List<Category> categories) {
                StringBuilder textToShow = new StringBuilder();
                for (Category category : categories) {
                    textToShow.append(String.format("%s (%02f)", category.getLabel(), category.getScore()));
                    textToShow.append("\n");
                }

                showNormalText(textToShow.toString());
            }
        });

        displayStrategyWeight.process(categories, new DisplayStrategy.INotifyDisplay() {
            @Override
            public void notify(List<Category> categories) {
                StringBuilder textToShow = new StringBuilder();
                for (Category category : categories) {
                    textToShow.append(String.format("%s (%02f)", category.getLabel(), category.getScore()));
                    textToShow.append("\n");
                }
                showWeightText(textToShow.toString());
            }
        });

/*        secondsCountUtils.run(new SecondsCountUtils.OnSecondReport() {
            @Override
            public void report(int count, long ms) {
                Log.d("classifyFrame run", "count:" + count + ", ms:" + ms);
            }
        });*/

        bitmap.recycle();
    }

    public void startClassifier(Context applicationContext, ConfigData configData ) throws Exception{
        //TfFileUtils.ModelFolderInfo info = TfFileUtils.checkModelFolder(new File(configData.getModelPath()));
        //classifier = TensorFlowImageClassifier.create(info.getModel(), info.getLabel(), configData.getWidth());
        classifier = new ModelWithMetaClassifier(applicationContext);
        workThreadHandler.runWorkThreadTimer(mClassifyRunnable, 500);
    }

    public void stopClassifier() {
        workThreadHandler.release();
    }

    private Bitmap findBitmapFormView() {
        TfPreviewActivity activity = weakReference.get();
        if (activity != null) {
            return activity.findBitmapFormView();
        }
        return null;
    }

    private void showNormalText(String text) {
        TfPreviewActivity activity = weakReference.get();
        if (activity != null) {
            activity.showTextLeft(text);
        }
    }

    public void showWeightText(String text) {
        TfPreviewActivity activity = weakReference.get();
        if (activity != null) {
            activity.showTextRight(text);
        }
    }
}
