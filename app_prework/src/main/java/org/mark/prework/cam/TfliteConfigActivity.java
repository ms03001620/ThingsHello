package org.mark.prework.cam;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.prework.R;
import org.mark.prework.cam.preview.TfPreviewActivity;
import org.mark.prework.db.DbMock;

import java.io.File;

/**
 * 配置Tflite摄像头识别所需要的选项
 */
public class TfliteConfigActivity extends AppCompatActivity {

    private TfliteConfigPresent mPresent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tflite_config);
        mPresent = new TfliteConfigPresent(this);

        findViewById(R.id.btn_model_check).setOnClickListener(mListener);
        findViewById(R.id.btn_model_pixel).setOnClickListener(mListener);
        findViewById(R.id.btn_open_camera).setOnClickListener(mListener);
    }

    private View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_model_check:
                    mPresent.doModelChoose();
                    break;
                case R.id.btn_model_pixel:
                    mPresent.doModelPixelSetting();
                    break;
                case R.id.btn_open_camera:
                    mPresent.doPreviewStart();
                    break;
            }

        }
    };

    public void onFileFolderOpen() {
        new ChooserDialog().with(this)
                .withFilter(true, false)
                .withStartFile(DbMock.getInstance().loadRecentAccessPath())
                .withDateFormat("HH:mm")
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String path, File pathFile) {
                        mPresent.saveModelPath(path);
                    }
                })
                .enableOptions(true)
                .build()
                .show();
    }

    public void onPixelChooseDialogOpen() {
        mPresent.saveWidth(224);
        mPresent.saveHeight(224);
    }

    public void onPreviewStart(ConfigData configData) {
        Intent intent = new Intent(this, TfPreviewActivity.class);
        intent.putExtra("config", configData);
        startActivity(intent);
    }

    public void onToastShow(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
