package org.mark.prework;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.prework.db.DbMock;

import java.io.File;

public class CheckActivity extends Activity {
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
                        .withStartFile(DbMock.getInstance().getRecentAccessPath())
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

                                DbMock.getInstance().setRecentAccessPath(pathFile.getParent());

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
                        .withStartFile(DbMock.getInstance().getRecentAccessPath())
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                mPresent.initImages(pathFile);
                                DbMock.getInstance().setRecentAccessPath(pathFile.getParent());

                                String reuslt = TfFileUtils.getPhotoListSuiffx(pathFile).toString();

                                addLogs(reuslt);
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

        findViewById(R.id.btn_to_grid).setEnabled(false);
        findViewById(R.id.btn_to_grid).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), GridActivity.class));
            }
        });
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
