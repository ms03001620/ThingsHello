package org.mark.thingshello.tensorflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.mark.base.executor.BlockProcess;
import org.mark.thingshello.video.sender.ConnectSelector;

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

    public String classifyFrame(final Bitmap bitmap) {
        if (factory == null) {
            return "";
        }

        String label;
        try {
            label = factory.feed(bitmap);
        } catch (Exception e) {
            label = e.toString();
        }
        return label;
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
                // 发送识别结果到客户端
                connectSelector.sendTextTcp(label);
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

    public int getWidth(){
        return factory.getWidth();
    }

    public int getHeight(){
        return factory.getHeight();
    }

}
