package org.mark.prework.cam;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.ffmpeg.FFUtils;
import org.mark.prework.R;
import org.mark.prework.TfFileUtils;
import org.mark.prework.cam.preview.TfPreviewActivity;
import org.mark.prework.db.DbMock;

import java.io.File;

/**
 * 配置Tflite摄像头识别所需要的选项
 */
public class TfliteConfigActivity extends PermissionActivity {

    private TfliteConfigPresent mPresent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tflite_config);
        mPresent = new TfliteConfigPresent(this);

        findViewById(R.id.btn_model_check).setOnClickListener(mListener);
        findViewById(R.id.btn_model_pixel).setOnClickListener(mListener);
        findViewById(R.id.btn_open_camera).setOnClickListener(mListener);
        //set default 224*224
        mPresent.saveWidth(224);
        mPresent.saveHeight(224);
        mPresent.saveIntervals(100);
        loadPrePath();

        System.out.println(FFUtils.avCodecInfo());
    }

    private void loadPrePath() {
        String modelPath = DbMock.getInstance().loadRecentModelPath();
        if (!TextUtils.isEmpty(modelPath)) {
            loadModel(new File(modelPath));
        } else {
            TextView textView = findViewById(R.id.text_model);
            textView.setText("please select tf model.");
        }
    }

    private View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_model_check:
                    mPresent.doModelChoose();
                    break;
                case R.id.btn_model_pixel:
                    break;
                case R.id.btn_open_camera:
                    checkPermissionCamera(new CheckPermissionCameraCallback() {
                        @Override
                        public void onRejected() {
                            onToastShow("need camera permission");
                        }

                        @Override
                        public void onAccept() {
                            mPresent.doPreviewStart();
                        }
                    });
                    break;
            }

        }
    };

    public void onFileFolderOpen() {
        ChooserDialog chooserDialog = new ChooserDialog(this)
                .withFilter(true, false)
                .withResources(R.string.title_choose,
                        R.string.title_choose, R.string.dialog_cancel)
                .withOptionResources(R.string.option_create_folder, R.string.options_delete,
                        R.string.new_folder_cancel, R.string.new_folder_ok)
                .withStartFile(DbMock.getInstance().loadRecentAccessPath())
                .disableTitle(false)
                .enableOptions(true)
                .titleFollowsDir(true)
                .displayPath(true)
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String path, File pathFile) {
                        loadModel(pathFile);
                    }
                });

        chooserDialog.build().show();
    }

    private void loadModel(File file){
        mPresent.saveModelPath(file.getPath());
        showModelInfo(file);
    }

    public void onPreviewStart(ConfigData configData) {
        Intent intent = new Intent(this, TfPreviewActivity.class);
        intent.putExtra("config", configData);
        startActivity(intent);
    }

    public void onToastShow(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void showModelInfo(File file) {
        TextView textView = findViewById(R.id.text_model);
        TfFileUtils.ModelFolderInfo info = TfFileUtils.checkModelFolder(file);

        if (info.isChecked()) {
            try {
                textView.setText(file.getName());
            } catch (Exception e) {
                textView.setText(e.toString());
            }
        } else {
            textView.setText(info.getError());
        }

        DbMock.getInstance().saveRecentModelPath(file.getPath());
        DbMock.getInstance().saveRecentAccessPath(file.getParent());
    }

}
