package org.mark.prework.cam.preview.category;

import org.tensorflow.lite.support.label.Category;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 普通排序（降序）
 */
class SortNormal implements DisplayStrategy.ISort{
    @Override
    public List<Category> sort(List<Category> categories) {
        Collections.sort(categories, new Comparator<Category>() {
            @Override
            public int compare(Category a, Category b) {
                if (a.getScore() > b.getScore()) {
                    return -1;
                } else if (a.getScore() < b.getScore()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        int num = Math.min(LEN, categories.size());
        return categories.subList(0, num);
    }
}
