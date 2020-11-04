package org.mark.camera;


import android.media.ImageReader;
import android.view.Surface;
import android.view.TextureView;

public class ConfigFactory implements IConfig {

    private IConfig config;

    public ConfigFactory(int width, int height, ImageReader.OnImageAvailableListener listenerImageAvailable) {
        config = new ConfigImageReader(width, height, listenerImageAvailable);
    }

    public ConfigFactory(int width, int height, TextureView textureView) {
        config = new ConfigTexture(width, height, textureView);
    }

    public int getWidth() {
        return config.getWidth();
    }

    public int getHeight() {
        return config.getHeight();
    }


    public Surface getSurface() {
        return config.getSurface();
    }

    @Override
    public void release() {
        config.release();
    }

    @Override
    public int getTemplateType() {
        return config.getTemplateType();
    }
}
