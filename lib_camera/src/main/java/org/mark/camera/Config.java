package org.mark.camera;


import android.media.ImageReader;

public class Config {
    private int width;
    private int height;
    private ImageReader.OnImageAvailableListener listenerImageAvailable;

    public Config(int width, int height, ImageReader.OnImageAvailableListener listenerImageAvailable) {
        this.width = width;
        this.height = height;
        this.listenerImageAvailable = listenerImageAvailable;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ImageReader.OnImageAvailableListener getListenerImageAvailable() {
        return listenerImageAvailable;
    }
}
