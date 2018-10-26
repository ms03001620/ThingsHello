package org.mark.prework.db;

import org.mark.base.PreferUtils;
import org.mark.prework.TfFileUtils;

import java.util.ArrayList;
import java.util.List;

public class DbMock {
    public static DbMock getInstance() {
        return ManagerHolder.instance;
    }

    private static class ManagerHolder {
        private static final DbMock instance = new DbMock();
    }

    private List<TfFileUtils.ImageAcc> images;
    private String folderName;
    private String labelString;

    private DbMock() {
        images = new ArrayList<>();
    }

    public void updateImages(List<TfFileUtils.ImageAcc> images) {
        this.images = new ArrayList<>(images);
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getLabelString() {
        return labelString;
    }

    public void setLabelString(String labelString) {
        this.labelString = labelString;
    }

    public String loadRecentAccessPath() {
        return PreferUtils.getInstance().get("recent_path", "");
    }

    public void saveRecentAccessPath(String recentAccessPath) {
        PreferUtils.getInstance().put("recent_path", recentAccessPath);
    }

    public void saveRecentModelPath(String modelPath) {
        PreferUtils.getInstance().put("recent_model_path", modelPath);
    }

    public String loadRecentModelPath() {
        return PreferUtils.getInstance().get("recent_model_path", "");
    }

    public List<TfFileUtils.ImageAcc> getImages() {
        return images;
    }

    public String getFolderName() {
        return folderName;
    }
}
