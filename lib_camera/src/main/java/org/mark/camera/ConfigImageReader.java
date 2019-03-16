package org.mark.camera;

import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

/**
 * Created by mark on 2019/3/16
 */
public class ConfigImageReader implements IConfig {

    private int width;
    private int height;
    private ImageReader.OnImageAvailableListener listenerImageAvailable;
    private ImageReader mImageReader;

    public ConfigImageReader(int width, int height, ImageReader.OnImageAvailableListener listenerImageAvailable) {

        this.width = width;
        this.height = height;
        this.listenerImageAvailable = listenerImageAvailable;

        mImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(listenerImageAvailable, new Handler());
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
    }
}
