package org.mark.prework.cam.preview.category;

import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * 优化排序（降序），在全部数据中找到最大的LEN个数据
 * 实现方式: 先组成LEN个数据队列，然后对队列进行排序
 * 然后在此基础上遍历全部数据，如符合"最大5个数据"的条件将其插入到队列中。
 * 数据值0或负数不参与排序。
 */
public class SortLimitList implements DisplayStrategy.ISort {

    @Override
    public List<Category> sort(List<Category> data) {
        LinkedList<Category> list = new LinkedList<>();
        boolean sorted = false;

        for (Category score : data) {
            if (Float.compare(score.getScore(), 0f) <= 0) {
                continue;
            }

            if (list.size() < LEN) {
                list.add(score);
            } else {
                if (!sorted) {
                    Collections.sort(list, new Comparator<Category>() {
                        @Override
                        public int compare(Category o1, Category o2) {
                            return Float.compare(o2.getScore(), o1.getScore());
                        }
                    });
                    sorted = true;
                }

                if (Float.compare(score.getScore(), list.getFirst().getScore()) >= 0) {
                    list.addFirst(score);
                } else {
                    if (Float.compare(score.getScore(), list.getLast().getScore()) > 0) {
                        for (int k = (list.size() - 2); k >= 0; k--) {
                            Category index = list.get(k);
                            if (Float.compare(score.getScore(), index.getScore()) < 0) {
                                list.add(k + 1, score);
                                break;
                            }
                        }
                    }
                }

                if (list.size() > LEN) {
                    list.removeLast();
                }
            }
        }

        if (!sorted) {
            Collections.sort(list, new Comparator<Category>() {
                @Override
                public int compare(Category o1, Category o2) {
                    return Float.compare(o2.getScore(), o1.getScore());
                }
            });
        }

        return new ArrayList<Category>(list);
    }
}
