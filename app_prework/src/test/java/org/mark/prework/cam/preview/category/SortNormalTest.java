package org.mark.prework.cam.preview.category;

import org.junit.Assert;
import org.junit.Test;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mark on 2020/10/27
 */
public class SortNormalTest {

    @Test
    public void sort() {
        SortNormal sortNormal = new SortNormal();

        List<Category> list = new ArrayList<>();
        list.add(new Category("", 3));
        list.add(new Category("", 1));
        list.add(new Category("", 2));
        list.add(new Category("", 0));
        list.add(new Category("", -1));
        list.add(new Category("", -2));

        list = sortNormal.sort(list);

        Assert.assertEquals(DisplayStrategy.ISort.LEN, list.size());

        Assert.assertEquals(0, Float.compare(3, list.get(0).getScore()));
        Assert.assertEquals(0, Float.compare(2, list.get(1).getScore()));
        Assert.assertEquals(0, Float.compare(1, list.get(2).getScore()));
        Assert.assertEquals(0, Float.compare(0, list.get(3).getScore()));
        Assert.assertEquals(0, Float.compare(-1, list.get(4).getScore()));
    }
}