package org.mark.base.thread;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    public static <T> List<List<T>> splitList(List<T> main, int num) {
        List<List<T>> result = new ArrayList<>();
        int size = main.size();
        if (num > size || num == 1) {
            result.add(main);
            return result;
        }

        int len = size / num;

        for (int start = 0; start < size; start += len) {
            int end = start + len;

            if (size - end < len) {
                end = end + (size - end);
            }

            List<T> sub = main.subList(start, end);
            result.add(sub);

            if (end == size) {
                break;
            }
        }

        return result;

    }
}
