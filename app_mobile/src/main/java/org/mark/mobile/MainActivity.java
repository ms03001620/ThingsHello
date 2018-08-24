package org.mark.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.utils.PreferUtils;

public class MainActivity extends AppCompatActivity {
    private TextView mTextLogs;
    private PreferUtils mPreferUtils;
    private Switch mSwitch;
    private EditText editTextHost;
    private EditText editTextPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        editTextHost = findViewById(R.id.edit_host);
        editTextPost = findViewById(R.id.edit_port);
        mTextLogs = findViewById(R.id.text_log);

        mSwitch = findViewById(R.id.fab);
        mSwitch.setOnCheckedChangeListener(mListener);
        initLastIp();
        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
    }

    private void initLastIp() {
        mPreferUtils = new PreferUtils(this);
        if (mPreferUtils.hasCached()) {
            editTextHost.setText(mPreferUtils.getHost());
            editTextPost.setText(mPreferUtils.getPort());
        }
    }

    public ClientMessageCallback mClientMessageCallback = new ClientMessageCallback() {
        @Override
        public void onReceiveMessage(byte[] bytes, int type) {
        }

        @Override
        public void onExceptionToReOpen(@NonNull final Exception e) {
            mSwitch.post(new Runnable() {
                @Override
                public void run() {
                    mSwitch.setOnCheckedChangeListener(null);
                    mSwitch.setChecked(false);
                    mSwitch.setOnCheckedChangeListener(mListener);
                    runUiText("exception:" + e.toString());
                }
            });
        }

        @Override
        public void onLogMessage(String message, @Nullable Exception e) {
            Log.e("Log:" , message, e);
        }

        @Override
        public void onStatusChange(@NonNull Status status) {
            runUiText("Status:" + status.name());
            if (status == Status.CONNECTED) {
                final String textHost = editTextHost.getText().toString();
                final String text = editTextPost.getText().toString();
                final int port = Integer.valueOf(text);
                mPreferUtils.save(textHost, port);
                startActivity(new Intent(MainActivity.this, CtrlActivity.class));
            }
        }
    };

    CompoundButton.OnCheckedChangeListener mListener = new CompoundButton.OnCheckedChangeListener() {
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
        super.onDestroy();
    }
}
