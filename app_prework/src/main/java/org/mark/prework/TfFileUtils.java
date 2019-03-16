package org.mark.prework;


import org.mark.lib_tensorflow.Classifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mark on 2018/10/24
 */
public class TfFileUtils {
    public static final class ModelFolderInfo {
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

    public static final class ImageAcc {
        public ImageAcc(String path, List<Classifier.Recognition> elements) {
            this.path = path;
            this.elements = new ArrayList<>(elements);
        }

        String path;
        List<Classifier.Recognition> elements;

        public String getInfo() {
            int id = elements.get(0).getId();
            String con = elements.get(0).getConfidenceString();

            return id + con;
        }

        public int getLabelIndex() {
            return elements.get(0).getId();
        }

        @Override
        public String toString() {
            return elements.toString() +
                    ", path='" + path + '\'';
        }

        public String getPath() {
            return path;
        }

        public boolean same(String index) {
            String id = String.valueOf(elements.get(0).getId());
            if (id.equals(index)) {
                return true;
            }
            return false;
        }
    }


    /**
     * 检查指定文件夹，找出model和label文件。如果不存在返回包括错误信息的对象ModelFolderInfo
     */
    public static ModelFolderInfo checkModelFolder(File folder) {
        boolean isFolder = folder.isDirectory();
        if (!isFolder) {
            return new ModelFolderInfo(null, null, false, "please select model folder");
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
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
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

    public static String getParentFolderName(File folder) {
        if (folder.isDirectory()) {
            int index = folder.getAbsolutePath().lastIndexOf("/");
            if (index != -1) {
                return folder.getAbsolutePath().substring(index);
            } else {
                return folder.getAbsolutePath();
            }
        } else {
            String folderUp = folder.getParent();

            int index = folderUp.lastIndexOf("/");
            if (index != -1) {
                return folderUp.substring(index);
            } else {
                return folderUp;
            }
        }
    }

}
