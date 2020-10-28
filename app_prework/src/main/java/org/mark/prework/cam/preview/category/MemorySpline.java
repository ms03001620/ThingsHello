package org.mark.prework.cam.preview.category;

import org.mark.prework.cam.preview.category.spline.Spline;
import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by mark on 2020/10/27
 */

public class MemorySpline implements DisplayStrategy.IMemory {

    Spline timeRankSpline;
    LinkedList<CategoryLog> logs;
    LinkedList<String> labels;
    int max = 10;

    public MemorySpline() {
        timeRankSpline = createTimeRankSpline();
        logs = new LinkedList<>();
        labels = new LinkedList<>();
    }

    private Spline createTimeRankSpline() {
        float[] x = {0.0f, 0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 1.00f};
        float[] y = {1.0f, 0.975f, 0.95f, 0.50f, 0.25f, 0.20f, 0.15f, 0.10f, 0.08f, 0.05f, 0.01f};//0.01, last not zero
        return Spline.createSpline(x, y);
    }

    @Override
    public List<Category> memorySort(List<Category> categories) {
        return memorySort(categories, System.currentTimeMillis());
    }

    public List<Category> memorySort(List<Category> categories, long time) {
        mergeLabel(categories);

        CategoryLog log = new CategoryLog(time, categories);
        if (logs.size() > max) {
            logs.removeFirst();
        }
        logs.add(log);

        final ArrayList<Category> result = new ArrayList<>();
        Map<Long, Float> ranks = computeShareLogRanks(logs);

        HashMap<String, Float> labelScore = new HashMap<>();

        for (String label : labels) {
            float score = 0;
            for (CategoryLog lg : logs) {
                if (lg.contains(label)) {
                    score += ranks.get(lg.id);
                }
            }
            labelScore.put(label, score);
            result.add(new Category(label, score));
        }

        Collections.sort(result, new Comparator<Category>() {
            @Override
            public int compare(Category o1, Category o2) {
                String label1 = o1.getLabel();
                String label2 = o2.getLabel();
                return Float.compare(labelScore.get(label2), labelScore.get(label1));
            }
        });

        return result;
    }

    private void mergeLabel(List<Category> categories) {
        int addTotal = 0;
        for (Category category : categories) {
            String label = category.getLabel();
            if (!labels.contains(label)) {
                if (labels.size() > max) {
                    labels.removeFirst();
                }
                labels.add(label);
                addTotal++;
            }
        }
        if (addTotal == categories.size()) {
            System.out.println("labels size:" + labels.size() + ", new insert:" + addTotal);
        }
    }

    public HashMap<Long, Float> computeShareLogRanks(List<CategoryLog> shareLogs) {
        long now = System.currentTimeMillis();

        ArrayList<Long> times = new ArrayList<>();
        for (CategoryLog shareLog : shareLogs) {
            times.add(now - shareLog.createMs);
        }

        ArrayList<Float> normedTimes = new ArrayList<>();

        if (times.size() > 1) {
            float minTime = Collections.min(times);
            float maxTime = Collections.max(times);
            float minNorm = 0;
            float maxNorm = 1;

            for (Long time : times) {
                float t = minNorm + (maxNorm - minNorm) * (time - minTime) / (maxTime - minTime);
                normedTimes.add(t);
            }
        } else {
            normedTimes.add(0.0f);
        }

        HashMap<Long, Float> ranks = new HashMap<>();
        for (int i = 0; i < shareLogs.size(); i++) {
            float time = normedTimes.get(i);
            float rank = timeRankSpline.interpolate(time);
            ranks.put(shareLogs.get(i).id, rank);
        }

        return ranks;
    }


    public static class CategoryLog{
        public long createMs;
        public long id;
        public List<Category> categories;

        public CategoryLog(long createMs, List<Category> categories) {
            //createMs -= 1000;//减少1000毫秒为了加大与当前时间差。这个时间作为相对时间参考不作为准确时间使用
            id = createMs;
            this.createMs = createMs;
            this.categories = categories;
        }

        public boolean contains(String label) {
            for (Category category : categories) {
                if (category.getLabel().equals(label)) {
                    return true;
                }
            }
            return false;
        }
    }
}
