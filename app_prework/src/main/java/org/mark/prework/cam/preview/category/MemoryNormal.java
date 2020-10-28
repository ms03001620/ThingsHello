package org.mark.prework.cam.preview.category;

import org.tensorflow.lite.support.label.Category;

import java.util.List;

/**
 * Created by mark on 2020/10/27
 */

class MemoryNormal implements DisplayStrategy.IMemory{
    @Override
    public List<Category> memorySort(List<Category> categories) {
        //无记忆分析，立即返回当前数据
        return categories;
    }
}
