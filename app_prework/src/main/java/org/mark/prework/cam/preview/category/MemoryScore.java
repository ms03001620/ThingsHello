package org.mark.prework.cam.preview.category;

import org.tensorflow.lite.support.label.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆统计。记住某个key的所有score保存在map中。
 * 然后根据score排序
 * 记忆移除。如果某个key在某次计算时没有出现，那么该key会从map中移除
 */

public class MemoryScore implements DisplayStrategy.IMemory {
    private HashMap<String, Float> memory;
    public MemoryScore() {
        memory = new HashMap<>();
    }

    @Override
    public List<Category> memorySort(List<Category> categories) {
        if (memory.size() > 0) {
            List<String> removeable = new ArrayList<>();
            for (Map.Entry<String, Float> entry : memory.entrySet()) {
                String key = entry.getKey();

                boolean find = false;

                for(Category score: categories){
                    if(score.getLabel().equals(key)){
                        find = true;
                        break;
                    }
                }
                if (!find) {
                    //统计未出现的key
                    removeable.add(key);
                }
            }
            for (String removekey : removeable) {
                //记忆中移除未出现key
                memory.remove(removekey);
                System.out.println("remove key:" + removekey);
            }
        }

        List<Category> totalable = new ArrayList<>();
        for(Category score: categories){
            String name = score.getLabel();
            if (!memory.containsKey(name)) {
                memory.put(name, 0f);
            }
            float total = score.getScore() + memory.get(name);
            memory.put(name, total);
            totalable.add(new Category(name, total));
        }

        Collections.sort(totalable, new Comparator<Category>() {
            @Override
            public int compare(Category o1, Category o2) {
                return Float.compare(o2.getScore(), o1.getScore());
            }
        });

        return totalable;
    }
}
