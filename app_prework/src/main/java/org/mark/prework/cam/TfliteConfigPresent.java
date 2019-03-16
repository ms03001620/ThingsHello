package org.mark.prework.cam;

/**
 * Created by mark on 2019/3/16
 */
class TfliteConfigPresent {

    private ConfigData mConfigData;

    private TfliteConfigActivity mActivity;

    public TfliteConfigPresent(TfliteConfigActivity activity) {
        mActivity = activity;
        mConfigData = new ConfigData();
    }


    public void doModelChoose() {
        mActivity.onFileFolderOpen();
    }

    public void doModelPixelSetting() {
        mActivity.onPixelChooseDialogOpen();
    }

    public void doPreviewStart() {
        if(!mConfigData.isValid()){
            mActivity.onToastShow("Config is not valid");
            return;
        }
        mActivity.onPreviewStart(mConfigData);
    }


    /**
     * 保存模型文件夹目录，文件夹下包括模型文件和标签文件共2个
     * @param modelFolderPath 包含model(tflite or lite)和label(txt）的文件夹
     */
    public void saveModelPath(String modelFolderPath) {
        mConfigData.modelPath = modelFolderPath;
    }

    public void saveWidth(int i) {
        mConfigData.width = i;
    }

    public void saveHeight(int i) {
        mConfigData.height = i;
    }

}
