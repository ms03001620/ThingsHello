package org.mark.prework.cam;

import org.mark.base.StringUtils;

import java.io.Serializable;

/**
 * Created by mark on 2019/3/16
 */
public class ConfigData implements Serializable {

    String modelPath;
    int width;
    int height;

    public boolean isValid() {
        return StringUtils.isNotNull(modelPath) &&
                width > 0 && height > 0;
    }

    public String getModelPath() {
        return modelPath;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
