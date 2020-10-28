package org.mark.prework.cam.preview.category;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by mark on 2020/10/27
 */
public class MemorySplineTest {

    MemorySpline memorySpline;


    @Before
    public void setup(){
        memorySpline = new MemorySpline();
    }

    @Test
    public void memorySort() {

        final long time = System.currentTimeMillis() / 1000 * 1000;

        for (int i = 1; i < 5; i++) {
            List<Category> categories = new ArrayList<>();
            categories.add(new Category("a", 100));
            categories.add(new Category("b", 80));
            categories.add(new Category("c", 30));

            long t = time + (i * 1000);

            List<Category> re = memorySpline.memorySort(categories, t);
            System.out.println(re);
        }

    }

    @Test
    public void computeShareLogRanks() {
        long now = System.currentTimeMillis();
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("a", 100));
        categories.add(new Category("b", 80));
        categories.add(new Category("c", 30));

        List<MemorySpline.CategoryLog> logs = new ArrayList<>();
        logs.add(new MemorySpline.CategoryLog(now, categories));
        HashMap<Long, Float> result = memorySpline.computeShareLogRanks(logs);
        Assert.assertEquals(1.0, result.get(now), 0);
    }

    @Test
    public void computeShareLogRanks2() {
        List<MemorySpline.CategoryLog> logs = mockLogs();

        HashMap<Long, Float> result = memorySpline.computeShareLogRanks(logs);

        System.out.println(result.size());

        //{Long@852} 1603792324000 -> {Float@853} 1.0
        //{Long@848} 1603792323000 -> {Float@849} 0.41666666
        //{Long@850} 1603792322000 -> {Float@851} 0.11666666
        //{Long@846} 1603792321000 -> {Float@847} 0.01

        //时间最近数据得分最高，相对较远的数据根据曲线分数降低 时间均相差1秒

    }


    private List<MemorySpline.CategoryLog> mockLogs(){
        List<MemorySpline.CategoryLog> logs = new ArrayList<>();

        final long time = System.currentTimeMillis() / 1000 * 1000;

        for (int i = 1; i < 5; i++) {
            List<Category> categories = new ArrayList<>();
            categories.add(new Category("a", 100));
            categories.add(new Category("b", 80));
            categories.add(new Category("c", 30));
            logs.add(new MemorySpline.CategoryLog(time + (i * 1000), categories));
        }
        return logs;
    }


}