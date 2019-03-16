package org.mark.prework;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.prework.db.DbMock;
import org.mark.prework.grid.GridActivity;

import java.io.File;

public class CheckActivity extends Activity {
    private TextView mTextView;

    private CheckPresent mPresent;

    private ProgressDialog mProcessDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check);
        mPresent = new CheckPresent(this);
        mTextView = findViewById(R.id.logs);

        // 选择识别模型文件夹
        findViewById(R.id.btn_choose_model).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(DbMock.getInstance().loadRecentAccessPath())
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                showModelInfo(pathFile);
                            }
                        })
                        .enableOptions(true)
                        .build()
                        .show();
            }
        });


        // 选择训练集文件夹
        findViewById(R.id.btn_choose_images).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(DbMock.getInstance().loadRecentAccessPath())
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                mPresent.initImages(pathFile);
                                ((TextView) findViewById(R.id.text_train)).setText(pathFile.getName());
                                DbMock.getInstance().saveRecentAccessPath(pathFile.getParent());
                                String result = TfFileUtils.getPhotoListSuiffx(pathFile).toString();
                                addLogs(result);
                            }
                        })
                        .enableOptions(true)
                        .build()
                        .show();
            }
        });

        // 开始识别
        findViewById(R.id.btn_choose_test_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPresent.isNoFileToPredict()) {
                    Toast.makeText(view.getContext(), "No images file found", Toast.LENGTH_LONG).show();
                    return;
                }
                mPresent.doPredictAll();
            }
        });

        // 查看识别结果
        findViewById(R.id.btn_to_grid).setEnabled(false);
        findViewById(R.id.btn_to_grid).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), GridActivity.class));
            }
        });

        mPresent.loadRecentData();
    }


    public void showProcessDialog(final int maxStep) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessDialog = new ProgressDialog(getActivity());
                mProcessDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProcessDialog.setCancelable(false);
                mProcessDialog.setTitle("Process images");
                mProcessDialog.setCanceledOnTouchOutside(false);
                mProcessDialog.setMax(maxStep);
                mProcessDialog.show();
            }
        });
    }

    public void updateProcessDialog(int process) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessDialog.incrementProgressBy(1);
            }
        });
    }

    public void hideProcessDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessDialog.dismiss();
            }
        });
    }

    public void showModelInfo(File file) {
        TextView textView = findViewById(R.id.text_model);
        TfFileUtils.ModelFolderInfo info = TfFileUtils.checkModelFolder(file);

        if (info.isChecked()) {
            try {
                mPresent.initModule(info, file);
                textView.setText(file.getName());
            } catch (Exception e) {
                textView.setText(e.toString());
            }
        } else {
            textView.setText(info.getError());
        }

        DbMock.getInstance().saveRecentAccessPath(file.getParent());
    }


    public void enableToGridButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.btn_to_grid).setEnabled(true);
            }
        });
    }


    public Activity getActivity() {
        return this;
    }


    public void addLogs(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String string = mTextView.getText().toString();
                mTextView.setText(message + "\n" + string);
            }
        });
    }

    public void clearLogs() {
        mTextView.setText("clear");
    }
}
