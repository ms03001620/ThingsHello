package org.mark.base;

import java.io.File;
import java.util.Random;

/**
 * Created by Mark on 2018/10/25
 */
public class FileUtils {


    public static boolean moveToTargetWithRandomName(File folder, File source) {
        if (folder.isDirectory()) {
            File newFile = new File(folder + File.separator
                    + createRandomNumberName()
                    + getPrefix(source));
            return source.renameTo(newFile);
        }
        return false;
    }


    public static String getPrefix(File file) {
        String fileName = file.getName();
        int index = fileName.lastIndexOf(".");
        if (index != -1) {
            return fileName.substring(index);
        }
        return "";
    }

    public static String createRandomNumberName() {
        Random random = new Random();
        return String.valueOf(Math.abs(random.nextInt()));
    }
}
