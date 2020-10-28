package org.mark.prework.cam;

import org.mark.base.StringUtils;

import java.io.Serializable;

/**
 * Created by mark on 2019/3/16
 */
public class ConfigData implements Serializable {

    // 图片识别模型文件存储路径
    String modelPath;
    // 图片宽度
    int width;
    // 图片高度
    int height;
    // 执行预测的间隔毫秒数
    long classifyIntervals;

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

    public long getClassifyIntervals() {
        return classifyIntervals;
    }
}
