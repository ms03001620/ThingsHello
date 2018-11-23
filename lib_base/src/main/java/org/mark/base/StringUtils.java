package org.mark.base;


public class StringUtils {

    public static boolean isNotNull(String string) {
        return string != null && string.length() != 0;
    }

    public static boolean isNull(String string) {
        return !isNotNull(string);
    }
}
