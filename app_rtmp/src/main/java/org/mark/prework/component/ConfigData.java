package org.mark.prework.component;

import java.io.Serializable;


public class ConfigData implements Serializable {
    // 图片宽度
    int width;
    // 图片高度
    int height;

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
