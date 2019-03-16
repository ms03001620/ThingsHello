package org.mark.camera;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;

/**
 * Created by mark on 2019/3/16
 */
public class ConfigTexture implements IConfig {

    private int width;
    private int height;
    private Surface surface;

    public ConfigTexture(int width, int height, TextureView textureView){
        this.width = width;
        this.height = height;
        SurfaceTexture texture = textureView.getSurfaceTexture();

        // We configure the size of default buffer to be the size of camera preview we want.
        texture.setDefaultBufferSize(width, height);

        // This is the output Surface we need to start preview.
        surface = new Surface(texture);
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
        return surface;
    }

    @Override
    public void release() {

    }
}
