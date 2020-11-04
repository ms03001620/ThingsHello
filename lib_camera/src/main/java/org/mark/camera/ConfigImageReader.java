package org.mark.camera;

import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

/**
 * Created by mark on 2019/3/16
 */
public class ConfigImageReader implements IConfig {

    private int width;
    private int height;
    private ImageReader.OnImageAvailableListener listenerImageAvailable;
    private ImageReader mImageReader;
    private HandlerThread mWorkThread;

    public ConfigImageReader(int width, int height, ImageReader.OnImageAvailableListener listenerImageAvailable) {


        this.width = width;
        this.height = height;
        this.listenerImageAvailable = listenerImageAvailable;

        mImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

        mWorkThread = new HandlerThread("CustomWorkThread");
        mWorkThread.start();
        mImageReader.setOnImageAvailableListener(listenerImageAvailable, new Handler(mWorkThread.getLooper()));
        Log.d("ConfigImageReader", "main looper:" + (Looper.getMainLooper() == mWorkThread.getLooper()));

    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    public ImageReader.OnImageAvailableListener getListenerImageAvailable() {
        return listenerImageAvailable;
    }

    @Override
    public void release() {
        mImageReader.close();
        mWorkThread.quitSafely();
    }

    @Override
    public int getTemplateType() {
        return 0;
    }
}
