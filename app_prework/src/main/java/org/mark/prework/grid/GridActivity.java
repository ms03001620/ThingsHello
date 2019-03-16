package org.mark.prework.grid;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.mark.prework.R;
import org.mark.prework.TfFileUtils;
import org.mark.prework.db.DbMock;
import org.mark.prework.grid.GridPresent;
import org.mark.prework.grid.ImageGridAdapter;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class GridActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private ImageGridAdapter mAdapter;

    private GridPresent mGridPresent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid);
        mGridPresent = new GridPresent(this);
        setTitle(DbMock.getInstance().getFolderName());

        TextView textView = findViewById(R.id.text);
        textView.setText(DbMock.getInstance().getLabelString());

        mRecyclerView = findViewById(R.id.recycler_recent);
        GridLayoutManager mgr = new GridLayoutManager(getActivity().getApplicationContext(), 5);


        mRecyclerView.setLayoutManager(mgr);
        mRecyclerView.setHasFixedSize(true);
        mAdapter = new ImageGridAdapter(new ImageGridAdapter.OnItemEvent() {
            @Override
            public void onItemClick(TfFileUtils.ImageAcc record, RecyclerView.ViewHolder viewHolder) {
                Log.v("GridActivity", "onItemClick:" + record.toString());
            }
        });

        mRecyclerView.setAdapter(mAdapter);
        mGridPresent.loadImages();

        checkLabelCount();
    }

    public void updateAdapter() {
        mAdapter.setData(DbMock.getInstance().getImages());
    }

    public void checkLabelCount() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                SparseIntArray idsMap = new SparseIntArray();

                List<TfFileUtils.ImageAcc> accs = DbMock.getInstance().getImages();

                for (TfFileUtils.ImageAcc acc : accs) {
                    int labelKey = acc.getLabelIndex();

                    int value = idsMap.get(labelKey, -1);
                    if (value != -1) {
                        value++;
                        idsMap.put(labelKey, value);
                    } else {
                        idsMap.put(labelKey, 1);
                    }
                }

                for(int i = 0; i < idsMap.size(); i++) {
                    int key = idsMap.keyAt(i);
                    int value = idsMap.get(key);
                    Log.d("GridActivity", "checkLabelCount key:" + key + ", value:" + value);
                }
            }
        }).start();

    }


    public Activity getActivity() {
        return this;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_select_all:
                mAdapter.selectAll();
                break;
            case R.id.action_hide:
                final View view = View.inflate(getActivity(), R.layout.dialog_aciton_hide, null);
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                final AlertDialog dialog = builder.setView(view).create();

                view.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText editText = view.findViewById(R.id.edit);
                        String text = editText.getText().toString();
                        mGridPresent.hide(text);
                        dialog.dismiss();
                    }
                });

                dialog.show();
                break;
            case R.id.action_move:
                // mGridPresent.move();
                new ChooserDialog().with(getActivity())
                        .withFilter(true, false)
                        .withStartFile(DbMock.getInstance().loadRecentAccessPath())
                        .withDateFormat("HH:mm")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                mGridPresent.moveToTarget(mAdapter.getChoicePaths(), pathFile);
                                mAdapter.notifyDataSetChanged();
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

                break;
            case R.id.action_delete:
                mGridPresent.moveToDeleteFolderWithChoiceList(mAdapter.getChoicePaths());
                mAdapter.notifyDataSetChanged();
                break;

        }


        return super.onOptionsItemSelected(item);
    }
}
