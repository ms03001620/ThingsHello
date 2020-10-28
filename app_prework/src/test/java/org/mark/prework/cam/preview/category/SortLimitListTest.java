package org.mark.prework.cam.preview.category;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mark.prework.RepeatRule;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Created by mark on 2020/10/27
 */
public class SortLimitListTest {
    @Rule
    public final RepeatRule repeatRule = new RepeatRule();
    SortLimitList sortLimitList;
    
    @Before
    public void setup(){
        sortLimitList = new SortLimitList();
    }

    @Test
    public void process() {
        List<Category> list = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            int score = i;
            if (i % 2 == 0) {
                score = i * 10;
            }
            list.add(new Category("a" + i, score));
        }
        Assert.assertEquals("[<Category \"a10\" (score=100.0)>, <Category \"a8\" (score=80.0)>, <Category \"a6\" (score=60.0)>, <Category \"a4\" (score=40.0)>, <Category \"a2\" (score=20.0)>]",
                Arrays.toString(sortLimitList.sort(list).toArray()));
    }


    @Test
    public void process2() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("", 15));
        list.add(new Category("", 12));
        list.add(new Category("", 4));
        list.add(new Category("", 1));
        list.add(new Category("", 3));

        Assert.assertEquals("[<Category \"\" (score=15.0)>, <Category \"\" (score=12.0)>, <Category \"\" (score=4.0)>, <Category \"\" (score=3.0)>, <Category \"\" (score=1.0)>]",
                Arrays.toString(sortLimitList.sort(list).toArray()));
    }

    @Test
    public void process3() {
        List<Category> list = new ArrayList<>();

        list.add(new Category("", 15));
        list.add(new Category("", 12));
        list.add(new Category("", 2));
        list.add(new Category("", 1));
        list.add(new Category("", 11));

        Assert.assertEquals("[<Category \"\" (score=15.0)>, <Category \"\" (score=12.0)>, <Category \"\" (score=11.0)>, <Category \"\" (score=2.0)>, <Category \"\" (score=1.0)>]",
                Arrays.toString(sortLimitList.sort(list).toArray()));
    }

    @Test
    public void process4() {
        List<Category> list = new ArrayList<>();

        list.add(new Category("", 3));
        list.add(new Category("", 15));
        list.add(new Category("", 12));
        list.add(new Category("", 2));
        list.add(new Category("", 1));
        list.add(new Category("", 11));
        list.add(new Category("", 13));

        Assert.assertEquals("[<Category \"\" (score=15.0)>, <Category \"\" (score=13.0)>, <Category \"\" (score=12.0)>, <Category \"\" (score=11.0)>, <Category \"\" (score=3.0)>]",
                Arrays.toString(sortLimitList.sort(list).toArray()));
    }

    @RepeatRule.Repeat(count = 5)
    @Test
    public void process5() {
        System.out.println("run process5");
        List<Category> list = new ArrayList<>();
        for (int i = 10000; i >=0; i--) {
            double num = Math.random();
            while (num == 0) {
                num = Math.random();
            }
            list.add(new Category("", (float) num));
        }

        String a = calcBigScore(list);
        String b = calcSort(list);
        Assert.assertEquals(a, b);
    }

    @Test
    public void process6() {
        List<Category> list = new ArrayList<>();
        Random random = new Random();
        for (int i = 10; i >=0; i--) {
            double num = random.nextInt(40);
            while (num == 0) {
                num = random.nextInt(40);
            }
            System.out.print(num+", ");
            list.add(new Category("", (float) num));
        }
        System.out.println();

        String a = calcBigScore(list);
        String b = calcSort(list);
        Assert.assertEquals(a, b);
    }

    @Test
    public void process7() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("", 35.0f));
        list.add(new Category("", 1.0f));
        list.add(new Category("", 7.0f));
        list.add(new Category("", 9.0f));
        list.add(new Category("", 35.0f));

        String a = calcBigScore(list);
        System.out.println(a);
        String b = calcSort(list);
        Assert.assertEquals(a, b);
    }

    @Test
    public void process8() {
        //24.0, 29.0, 20.0,    36.0, 35.0, 39.0,    39.0, 34.0, 23.0,    24.0, 4.0,
        List<Category> list = new ArrayList<>();
        list.add(new Category("", 24));
        list.add(new Category("", 29));
        list.add(new Category("", 20));

        list.add(new Category("", 36));
        list.add(new Category("", 35));
        list.add(new Category("", 39));

        list.add(new Category("", 39));
        list.add(new Category("", 34));
        list.add(new Category("", 23));

        list.add(new Category("", 24));
        list.add(new Category("", 4));

        String a = calcBigScore(list);
        String b = calcSort(list);
        Assert.assertEquals(a, b);
    }

    @Test
    public void process9() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("", 3));
        list.add(new Category("", 1));
        list.add(new Category("", 2));
        list.add(new Category("", 0));
        list.add(new Category("", -1));

        list = sortLimitList.sort(list);

        Assert.assertEquals(3, list.size());//without 0, -x

        Assert.assertEquals(0, Float.compare(3, list.get(0).getScore()));
        Assert.assertEquals(0, Float.compare(2, list.get(1).getScore()));
        Assert.assertEquals(0, Float.compare(1, list.get(2).getScore()));
    }

    private String calcBigScore(List<Category> list){
        long start = System.nanoTime();
        List<Category> l = sortLimitList.sort(list);
        System.out.println("pass a:"+(System.nanoTime()-start));
        return Arrays.toString(l.toArray());
    }

    private String calcSort(List<Category> list){
        long st = System.nanoTime();
        Collections.sort(list, new Comparator<Category>() {
            @Override
            public int compare(Category o1, Category o2) {
                return Float.compare(o2.getScore(), o1.getScore());
            }
        });
        List<Category> l = new ArrayList<>();
        for (int i = 0; i < SortLimitList.LEN; i++) {
            l.add(list.get(i));
        }
        System.out.println("pass b:" + (System.nanoTime() - st));
        return Arrays.toString(l.toArray());
    }
}