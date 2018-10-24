package org.mark.check;



import org.mark.lib_tensorflow.Classifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mark on 2018/10/24
 */
public class TfFileUtils {
    static final class ModelFolderInfo {
        public ModelFolderInfo(String model, String label, boolean checked, String error) {
            this.model = model;
            this.label = label;
            this.checked = checked;
            this.error = error;
        }

        private String model;
        private String label;
        private boolean checked;
        private String error;

        boolean isChecked() {
            return checked;
        }


        public String getModel() {
            return model;
        }

        public String getLabel() {
            return label;
        }

        public String getError() {
            return error;
        }

        @Override
        public String toString() {
            return "ModelFolderInfo{" +
                    "model='" + model + '\'' +
                    ", label='" + label + '\'' +
                    ", checked=" + checked +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    static final class ImageAcc {
        public ImageAcc(String path, List<Classifier.Recognition> elements) {
            this.path = path;
            this.elements = elements;
        }

        String path;
        List<Classifier.Recognition> elements;

        @Override
        public String toString() {
            return "ImageAcc{" +
                    elements.toString() +
                    ", path='" + path + '\'' +
                    '}';
        }
    }


    /**
     * 检查指定文件夹，找出model和label文件。如果不存在返回包括错误信息的对象ModelFolderInfo
     */
    public static ModelFolderInfo checkModelFolder(File folder) {
        boolean isFolder = folder.isDirectory();
        if (!isFolder) {
            return new ModelFolderInfo(null, null, false, "not folder");
        }

        File[] listOfFiles = folder.listFiles();


        if (listOfFiles.length != 2) {
            return new ModelFolderInfo(null, null, false, "folder size error:" + listOfFiles.length);
        }

        String label = null;
        String model = null;

        boolean fileError = false;

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                String name = listOfFiles[i].getName();
                if (name.endsWith(".txt")) {
                    if (label == null) {
                        label = folder.getAbsolutePath() + File.separator + name;
                    } else {
                        fileError = true;
                    }
                } else if (name.endsWith(".lite") || name.endsWith(".tflite")) {
                    if (model == null) {
                        model = folder.getAbsolutePath() + File.separator + name;
                    } else {
                        fileError = true;
                    }
                }

                if (fileError) {
                    break;
                }
            }
        }

        if (!fileError && label != null && model != null) {
            return new ModelFolderInfo(model, label, true, "not error");
        } else {
            return new ModelFolderInfo(null, null, false, "content error");
        }
    }

    public static List<String> getPhotoList(File folder) {
        List<String> list = new ArrayList<>();
        if (folder.isDirectory()) {
            String head = folder.getAbsolutePath() + File.separator;
            String[] path = folder.list();

            for (String name : path) {
                if (name.endsWith(".png") || name.endsWith(".jpg")) {
                    list.add(head + name);
                }
            }
        }
        return list;
    }

    public static List<String> getPhotoListSuiffx(File folder) {
        List<String> list = new ArrayList<>();
        if (folder.isDirectory()) {
            String[] path = folder.list();
            for (String name : path) {
                int index = name.lastIndexOf(".");

                if (index != -1) {
                    String endd = name.substring(index);
                    if (!list.contains(endd)) {
                        list.add(endd);
                    }
                } else {
                    list.add(name);
                }
            }
        }
        return list;
    }

}
