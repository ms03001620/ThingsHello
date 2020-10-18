package org.mark.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.JsonReceiver;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.utils.PreferUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {
    private TextView mTextLogs;
    private PreferUtils mPreferUtils;
    private Switch mSwitch;
    private AutoCompleteTextView editTextHost;
    private EditText editTextPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Version: " + BuildConfig.VERSION_NAME);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        org.mark.base.PreferUtils.getInstance().init(this);
        editTextHost = findViewById(R.id.edit_host);
        editTextPost = findViewById(R.id.edit_port);
        mTextLogs = findViewById(R.id.text_log);

        mSwitch = findViewById(R.id.fab);
        mSwitch.setOnCheckedChangeListener(mSwitchListener);
        findViewById(R.id.btnCtrl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CtrlActivity.class));
            }
        });
        setupAutoFillIp();
        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
    }

    private void setupAutoFillIp() {
        mPreferUtils = new PreferUtils(this);
        String[] autoString = mPreferUtils.getAddress();
        if (autoString != null && autoString.length > 0) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, autoString);
            // 0是最近使用的
            editTextHost.setText(autoString[0]);
            editTextHost.setAdapter(adapter);

            editTextHost.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        editTextHost.showDropDown();
                    } else {
                        editTextHost.dismissDropDown();
                    }
                }
            });

            editTextHost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (editTextHost.hasFocus()) {
                        editTextHost.showDropDown();
                    }
                }
            });
        }
    }

    public JsonReceiver mClientMessageCallback = new JsonReceiver() {
        @Override
        public void onReceiverJson(String json, int type) {
            Log.d("MainActivity", "onReceiverJson type:" + type + ", json:" + json);
        }


        @Override
        public void onExceptionToReOpen(@NonNull final Exception e) {
            mSwitch.post(new Runnable() {
                @Override
                public void run() {
                    mSwitch.setOnCheckedChangeListener(null);
                    mSwitch.setChecked(false);
                    mSwitch.setOnCheckedChangeListener(mSwitchListener);
                    runUiText("exception:" + e.toString());
                }
            });
        }

        @Override
        public void onLogMessage(String message, @Nullable Exception e) {
            if (e != null) {
                Log.e("MainActivity", message, e);
            } else {
                Log.d("MainActivity", message);
            }
        }

        @Override
        public void onStatusChange(@NonNull final Status status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    runUiText("Status:" + status.name());
                    findViewById(R.id.btnCtrl).setEnabled(status == Status.CONNECTED);
                    if (status == Status.CONNECTED) {
                        final String textHost = editTextHost.getText().toString();
                        final String text = editTextPost.getText().toString();
                        final int port = Integer.valueOf(text);
                        mPreferUtils.add(textHost, port);
                    }
                }
            });
        }
    };

    // 连接启动开关
    CompoundButton.OnCheckedChangeListener mSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean on) {
            if (on) {
                try {
                    final String textHost = editTextHost.getText().toString();
                    final String text = editTextPost.getText().toString();
                    final int port = Integer.valueOf(text);
                    ConnectedManager.getInstance().init(textHost, port);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "输入错误", Toast.LENGTH_LONG).show();
                }

            } else {
                ConnectedManager.getInstance().stop();
            }
        }
    };

    public void runUiText(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String string = mTextLogs.getText().toString();
                mTextLogs.setText(message + "\n" + string);
            }
        });
    }

    @Override
    protected void onDestroy() {
        ConnectedManager.getInstance().removeCallback(mClientMessageCallback);
        ConnectedManager.getInstance().stop();
        super.onDestroy();
    }
}
