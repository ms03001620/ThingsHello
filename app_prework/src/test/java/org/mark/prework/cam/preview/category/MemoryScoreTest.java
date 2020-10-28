package org.mark.prework.cam.preview.category;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mark on 2020/10/27
 */
public class MemoryScoreTest {

    List<Category> list;
    MemoryScore memoryScore;

    @Before
    public void setup() {
        memoryScore = new MemoryScore();
        list = new ArrayList<>();
        list.add(new Category("a", 3));
        list.add(new Category("b", 2));
        list.add(new Category("c", 1));
        list.add(new Category("d", 4));
    }

    @Test
    public void calc() {
        List<Category> result = memoryScore.memorySort(list);

        Assert.assertEquals(4, result.get(0).getScore(), 0);
        Assert.assertEquals(3, result.get(1).getScore(), 0);
        Assert.assertEquals(2, result.get(2).getScore(), 0);
        Assert.assertEquals(1, result.get(3).getScore(), 0);

        list = new ArrayList<>();
        list.add(new Category("a", 1));
        list.add(new Category("b", 1));
        list.add(new Category("c", 100));
        list.add(new Category("d", 1));

        result = memoryScore.memorySort(list);

        Assert.assertEquals(101f, result.get(0).getScore(), 0);
        Assert.assertEquals(5f, result.get(1).getScore(), 0);
        Assert.assertEquals(4f, result.get(2).getScore(), 0);
        Assert.assertEquals(3f, result.get(3).getScore(), 0);
    }

    @Test
    public void calc2() {
        memoryScore.memorySort(list);
        list.clear();
        list.add(new Category("a", 3));

        Assert.assertEquals(6, memoryScore.memorySort(list).get(0).getScore(), 0);
    }

}