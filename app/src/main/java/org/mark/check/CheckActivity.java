package org.mark.check;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.thingshello.R;

import java.io.File;
import java.util.List;

public class CheckActivity extends Activity {
    private String mPathCurrent;
    private TextView mTextView;

    private CheckPresent mPresent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check);
        mPresent = new CheckPresent(this);
        mTextView = findViewById(R.id.logs);

        findViewById(R.id.btn_choose_model).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(mPathCurrent)
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                TfFileUtils.ModelFolderInfo info = TfFileUtils.checkModelFolder(pathFile);

                                if (info.isChecked()) {
                                    try {
                                        mPresent.initModule(info);
                                    } catch (Exception e) {
                                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(getApplicationContext(), info.getError(), Toast.LENGTH_SHORT).show();
                                }

                                mPathCurrent = pathFile.getParent();
                            }
                        })
                        .enableOptions(true)
                        .withOnBackPressedListener(new ChooserDialog.OnBackPressedListener() {
                            @Override
                            public void onBackPressed(AlertDialog dialog) {
                                dialog.dismiss();
                            }
                        })
                        .build()
                        .show();
            }
        });


        findViewById(R.id.btn_choose_images).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(mPathCurrent)
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                mPresent.initImages(pathFile);
                                mPathCurrent = pathFile.getParent();
                            }
                        })
                        .enableOptions(true)
                        .withOnBackPressedListener(new ChooserDialog.OnBackPressedListener() {
                            @Override
                            public void onBackPressed(AlertDialog dialog) {
                                dialog.dismiss();
                            }
                        })
                        .build()
                        .show();
            }
        });


        findViewById(R.id.btn_choose_files).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(mPathCurrent)
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                String reuslt = TfFileUtils.getPhotoListSuiffx(pathFile).toString();

                                addLogs(reuslt);
                                Log.d("CheckActivity", reuslt);
                                mPathCurrent = pathFile.getParent();
                            }
                        })
                        .enableOptions(true)
                        .withOnBackPressedListener(new ChooserDialog.OnBackPressedListener() {
                            @Override
                            public void onBackPressed(AlertDialog dialog) {
                                dialog.dismiss();
                            }
                        })
                        .build()
                        .show();
            }
        });


        findViewById(R.id.btn_choose_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = mPresent.doPredictRandom();
                addLogs(result);
            }
        });

        findViewById(R.id.btn_choose_test_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresent.doPredictAll();
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
}
