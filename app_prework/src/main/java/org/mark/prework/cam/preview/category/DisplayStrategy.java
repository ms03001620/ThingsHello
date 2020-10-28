package org.mark.prework.cam.preview.category;

import org.tensorflow.lite.support.label.Category;

import java.util.List;

/**
 * Created by mark on 2020/10/27
 */

public class DisplayStrategy {
    public interface INotifyDisplay{
        void notify(List<Category> categories);
    }

    public interface ISort {
        int LEN = 5;//默认只找5个最大数
        List<Category> sort(List<Category> categories);
    }

    public interface IMemory {
        List<Category> memorySort(List<Category> categories);
    }

    ISort iSort;
    IMemory iMemory;
    INotifyDisplay iNotifyDisplay;

    public DisplayStrategy(){
        iSort = new SortNormal();
        iMemory = new MemoryNormal();
    }

    public DisplayStrategy(ISort iSort, IMemory iMemory){
        this.iSort = iSort;
        this.iMemory = iMemory;
    }

    public void process(List<Category> categories, INotifyDisplay notifyDisplay){
        categories = iSort.sort(categories);
        categories = iMemory.memorySort(categories);
        notifyDisplay.notify(categories);
    }
}
