package org.mark.mobile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.JsonReceiver;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.databinding.ActivityMainBinding;
import org.mark.mobile.ui.cyber.CutShapeHelper;
import org.mark.mobile.ui.cyber.ScanlineDrawable;
import org.mark.mobile.utils.PreferUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Cyberpunk 2077 Connect screen — wires to {@link ConnectedManager}.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String EXTRA_REFUSED = "extra_refused";
    /** Debug only: force UI state for screenshots (idle/connecting/online/refused). */
    public static final String EXTRA_DEBUG_STATE = "debug_state";

    private enum UiState {
        IDLE,
        CONNECTING,
        ONLINE,
        REFUSED
    }

    private ActivityMainBinding binding;
    private PreferUtils mPreferUtils;

    private UiState mUiState = UiState.IDLE;
    private boolean mConnectRequested;
    private boolean mUserCancelled;
    private boolean mIgnoreNextNoConnect;
    private String mLastErrorMessage = "";
    private ObjectAnimator mBeamAnimator;
    private int mBarWidthPx;
    private float mCutM;
    private float mCutL;
    private float mCutInputBr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.cp_bg));
        setContentView(binding.getRoot());

        mPreferUtils = PreferUtils.getInstance(this);

        mBarWidthPx = dp(3);
        mCutM = dp(14);
        mCutL = dp(22);
        mCutInputBr = dp(14);

        binding.scanlineOverlay.setBackground(new ScanlineDrawable(
                getResources(), ContextCompat.getColor(this, R.color.scanline)));
        binding.scanlineOverlay.setAlpha(1f);

        applyCyberChrome();
        setupInputs();
        setupActions();
        populateRecent();
        showState(UiState.IDLE);

        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(EXTRA_REFUSED, false)) {
            intent.removeExtra(EXTRA_REFUSED);
            mConnectRequested = false;
            mIgnoreNextNoConnect = false;
            mLastErrorMessage = "";
            ConnectedManager.getInstance().stop();
            showState(UiState.REFUSED);
            return;
        }
        if (BuildConfig.DEBUG) {
            String s = intent.getStringExtra(EXTRA_DEBUG_STATE);
            if ("preview_demo".equals(s) || "preview_demo_hidden".equals(s)) {
                Intent pv = new Intent(this, org.mark.mobile.preview.H264Activity.class);
                pv.putExtra(EXTRA_DEBUG_STATE, s.substring("preview_".length()));
                startActivity(pv);
                return;
            }
            if ("ctrl".equals(s) || "ctrl_demo".equals(s)) {
                // Debug-only shortcut to open the (non-exported) control page for screenshots.
                Intent ctrl = new Intent(this, CtrlActivity.class);
                ctrl.putExtra(EXTRA_DEBUG_STATE, "ctrl_demo".equals(s) ? "demo" : "none");
                startActivity(ctrl);
                return;
            }
            if (s != null) {
                try {
                    showState(UiState.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void applyCyberChrome() {
        int panel = ContextCompat.getColor(this, R.color.cp_panel);
        int yellow = ContextCompat.getColor(this, R.color.cp_yellow);
        int cyan = ContextCompat.getColor(this, R.color.cp_cyan);
        int red = ContextCompat.getColor(this, R.color.cp_red);
        int cyan07 = ContextCompat.getColor(this, R.color.cyan_07);
        int cyan12 = ContextCompat.getColor(this, R.color.cyan_12);
        int red13 = ContextCompat.getColor(this, R.color.red_13);

        // TR/BL cut ~28x27 on 350x150 → use ~28dp / 27dp
        CutShapeHelper.apply(binding.cardCar, cyan07, 0, dp(28), 0, dp(27));

        CutShapeHelper.applyWithLeftBar(binding.wrapIp, panel, yellow, mBarWidthPx, 0, 0, mCutInputBr, 0);
        CutShapeHelper.applyWithLeftBar(binding.wrapPort, panel, yellow, mBarWidthPx, 0, 0, mCutInputBr, 0);

        CutShapeHelper.applyWithLeftBar(binding.cardStatusOnline, cyan12, cyan, mBarWidthPx, 0, 0, 0, mCutM);
        CutShapeHelper.applyWithLeftBar(binding.cardStatusRefused, red13, red, mBarWidthPx, 0, 0, 0, mCutM);

        CutShapeHelper.applyButton(binding.btnConnect, yellow,
                ContextCompat.getColor(this, R.color.yellow_30), 0, mCutL, 0, mCutL);

        // Retry: red cut BR ~20%x30% of 54x32 ≈ 11 x 10
        CutShapeHelper.applyButton(binding.btnRetry, red,
                Color.WHITE, 0, 0, dp(11), 0);

        if (binding.progressConnect.getIndeterminateDrawable() != null) {
            binding.progressConnect.getIndeterminateDrawable().setColorFilter(
                    ContextCompat.getColor(this, R.color.cp_on_yellow), PorterDuff.Mode.SRC_IN);
        }

        startBeamAnimation();
    }

    private void startBeamAnimation() {
        if (binding.scanBeam == null) {
            return;
        }
        binding.scanBeam.post(new Runnable() {
            @Override
            public void run() {
                View parent = (View) binding.scanBeam.getParent();
                if (parent == null || parent.getHeight() == 0) {
                    binding.scanBeam.post(this);
                    return;
                }
                float travel = parent.getHeight() * 0.5f;
                mBeamAnimator = ObjectAnimator.ofFloat(binding.scanBeam, View.TRANSLATION_Y, -travel * 0.3f, travel * 0.3f);
                mBeamAnimator.setDuration(2000);
                mBeamAnimator.setRepeatMode(ValueAnimator.REVERSE);
                mBeamAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mBeamAnimator.setInterpolator(new LinearInterpolator());
                mBeamAnimator.start();
            }
        });
    }

    private void setupInputs() {
        String[] recent = mPreferUtils.getAddress();
        if (recent != null && recent.length > 0 && !TextUtils.isEmpty(recent[0])) {
            binding.editHost.setText(recent[0]);
        }
        if (mPreferUtils.hasCached() && mPreferUtils.getPortNumber() > 0) {
            binding.editPort.setText(mPreferUtils.getPort());
        }

        View.OnFocusChangeListener focusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                View wrap = v == binding.editHost ? binding.wrapIp : binding.wrapPort;
                int yellow = ContextCompat.getColor(MainActivity.this, R.color.cp_yellow);
                int panel = ContextCompat.getColor(MainActivity.this, R.color.cp_panel);
                if (hasFocus) {
                    CutShapeHelper.applyWithLeftBar(wrap, panel, yellow, dp(4), 0, 0, mCutInputBr, 0);
                    // 1dp yellow 40% stroke approximated by slight fill tint
                } else {
                    CutShapeHelper.applyWithLeftBar(wrap, panel, yellow, mBarWidthPx, 0, 0, mCutInputBr, 0);
                }
            }
        };
        binding.editHost.setOnFocusChangeListener(focusListener);
        binding.editPort.setOnFocusChangeListener(focusListener);

        binding.editHost.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                binding.textIpError.setVisibility(View.GONE);
            }
        });
    }

    private void setupActions() {
        binding.btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUiState == UiState.CONNECTING) {
                    return;
                }
                if (mUiState == UiState.ONLINE) {
                    openControl();
                    return;
                }
                attemptConnect();
            }
        });

        binding.btnConnect.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mUiState == UiState.CONNECTING) {
                    return false;
                }
                int yellow = ContextCompat.getColor(MainActivity.this, R.color.cp_yellow);
                int pressed = ContextCompat.getColor(MainActivity.this, R.color.cp_yellow_pressed);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        CutShapeHelper.applyButton(binding.btnConnect, pressed,
                                ContextCompat.getColor(MainActivity.this, R.color.yellow_30),
                                0, mCutL, 0, mCutL);
                        binding.btnConnect.setTranslationY(dp(1));
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        CutShapeHelper.applyButton(binding.btnConnect, yellow,
                                ContextCompat.getColor(MainActivity.this, R.color.yellow_30),
                                0, mCutL, 0, mCutL);
                        binding.btnConnect.setTranslationY(0);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });

        binding.btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptConnect();
            }
        });
    }

    private void populateRecent() {
        binding.listRecent.removeAllViews();
        String[] addresses = mPreferUtils.getAddress();
        if (addresses == null || addresses.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("—");
            empty.setTextColor(ContextCompat.getColor(this, R.color.cp_text_dim));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setAlpha(0.5f);
            binding.listRecent.addView(empty);
            return;
        }
        int max = Math.min(addresses.length, 5);
        for (int i = 0; i < max; i++) {
            final String host = addresses[i];
            if (TextUtils.isEmpty(host)) {
                continue;
            }
            TextView row = new TextView(this);
            row.setText(host);
            row.setTextColor(ContextCompat.getColor(this, R.color.cp_text_dim));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.setPadding(0, dp(3), 0, dp(3));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    binding.editHost.setText(host);
                    binding.editHost.setSelection(host.length());
                }
            });
            binding.listRecent.addView(row);
        }
    }

    private void attemptConnect() {
        hideKeyboard();
        final String host = binding.editHost.getText() != null
                ? binding.editHost.getText().toString().trim() : "";
        final String portText = binding.editPort.getText() != null
                ? binding.editPort.getText().toString().trim() : "";
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(portText)) {
            binding.textIpError.setText(R.string.cp_input_error);
            binding.textIpError.setVisibility(View.VISIBLE);
            int red = ContextCompat.getColor(this, R.color.cp_red);
            int panel = ContextCompat.getColor(this, R.color.cp_panel);
            CutShapeHelper.applyWithLeftBar(binding.wrapIp, panel, red, mBarWidthPx, 0, 0, mCutInputBr, 0);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException("port");
            }
        } catch (Exception e) {
            binding.textIpError.setText(R.string.cp_input_error);
            binding.textIpError.setVisibility(View.VISIBLE);
            return;
        }

        mUserCancelled = false;
        mConnectRequested = true;
        mIgnoreNextNoConnect = true;
        mLastErrorMessage = "";
        showState(UiState.CONNECTING);
        ConnectedManager.getInstance().stop();
        ConnectedManager.getInstance().init(host, port);
    }

    private void showState(UiState state) {
        mUiState = state;
        binding.cardStatusOnline.setVisibility(View.GONE);
        binding.cardStatusRefused.setVisibility(View.GONE);
        binding.progressConnect.setVisibility(View.GONE);
        binding.btnConnect.setEnabled(true);
        binding.btnConnect.setAlpha(1f);
        binding.textConnect.setAlpha(1f);

        int yellow = ContextCompat.getColor(this, R.color.cp_yellow);
        int cyan = ContextCompat.getColor(this, R.color.cp_cyan);

        switch (state) {
            case IDLE:
                binding.textConnect.setText(R.string.cp_connect);
                CutShapeHelper.applyButton(binding.btnConnect, yellow,
                        ContextCompat.getColor(this, R.color.yellow_30), 0, mCutL, 0, mCutL);
                break;
            case CONNECTING:
                binding.textConnect.setText(R.string.cp_connecting);
                binding.progressConnect.setVisibility(View.VISIBLE);
                binding.textStatusOnlineTitle.setText(R.string.cp_status_connecting_title);
                binding.textStatusOnlineDesc.setText(R.string.cp_status_connecting_desc);
                binding.cardStatusOnline.setVisibility(View.VISIBLE);
                binding.btnConnect.setEnabled(false);
                binding.btnConnect.setAlpha(0.85f);
                break;
            case ONLINE:
                binding.textConnect.setText(R.string.cp_connect);
                String port = binding.editPort.getText() != null
                        ? binding.editPort.getText().toString().trim() : "8010";
                binding.textStatusOnlineTitle.setText(R.string.cp_status_online_title);
                binding.textStatusOnlineDesc.setText(getString(R.string.cp_status_online_desc, port));
                binding.cardStatusOnline.setVisibility(View.VISIBLE);
                break;
            case REFUSED:
                binding.textConnect.setText(R.string.cp_connect);
                if (isTimeoutError(mLastErrorMessage)) {
                    binding.textStatusRefusedDesc.setText(R.string.cp_status_timeout_desc);
                } else {
                    binding.textStatusRefusedDesc.setText(R.string.cp_status_refused_desc);
                }
                binding.cardStatusRefused.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }
    private boolean isTimeoutError(String msg) {
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("enotunreach")
                || lower.contains("network is unreachable")
                || lower.contains("no route");
    }

    private void openControl() {
        Intent intent = new Intent(MainActivity.this, CtrlActivity.class);
        intent.putExtra(CtrlActivity.EXTRA_HOST, binding.editHost.getText().toString().trim());
        try {
            intent.putExtra(CtrlActivity.EXTRA_PORT, Integer.parseInt(binding.editPort.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
        }
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateRecent(); // CtrlActivity may have saved a newly-proven address
    }

    public JsonReceiver mClientMessageCallback = new JsonReceiver() {
        @Override
        public void onReceiverJson(String json, int type) {
            Log.d(TAG, "onReceiverJson type:" + type + ", json:" + json);
        }

        @Override
        public void onExceptionToReOpen(@NonNull final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLastErrorMessage = e != null ? String.valueOf(e.getMessage()) : "";
                    if (mConnectRequested && !mUserCancelled) {
                        showState(UiState.REFUSED);
                    } else if (mUiState != UiState.REFUSED) {
                        showState(UiState.IDLE);
                    }
                }
            });
        }

        @Override
        public void onLogMessage(String message, @Nullable Exception e) {
            if (e != null) {
                Log.e(TAG, message, e);
                mLastErrorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            } else {
                Log.d(TAG, message);
            }
        }

        @Override
        public void onStatusChange(@NonNull final Status status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleStatus(status);
                }
            });
        }
    };

    private void handleStatus(ClientMessageCallback.Status status) {
        switch (status) {
            case CONNECTING:
                if (mConnectRequested) {
                    showState(UiState.CONNECTING);
                }
                break;
            case CONNECTED:
                // Connect succeeded: any later NO_CONNECT is a real disconnect, not our own stop().
                mIgnoreNextNoConnect = false;
                showState(UiState.ONLINE);
                // Navigate to control after a brief beat so online card is visible
                binding.btnConnect.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mUiState == UiState.ONLINE && ConnectedManager.getInstance().isConnected()) {
                            openControl();
                        }
                    }
                }, 350);
                break;
            case NO_CONNECT:
                if (mIgnoreNextNoConnect) {
                    mIgnoreNextNoConnect = false;
                    // Likely from stop() before a new init; keep CONNECTING UI.
                    if (mConnectRequested && mUiState == UiState.CONNECTING) {
                        break;
                    }
                }
                if (mUiState == UiState.REFUSED) {
                    break;
                }
                if (mUserCancelled) {
                    showState(UiState.IDLE);
                } else if (mConnectRequested) {
                    // Connect attempt ended without success → refused / timeout
                    showState(UiState.REFUSED);
                } else {
                    showState(UiState.IDLE);
                }
                break;
            default:
                break;
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onDestroy() {
        if (mBeamAnimator != null) {
            mBeamAnimator.cancel();
        }
        ConnectedManager.getInstance().removeCallback(mClientMessageCallback);
        // Main stays under CtrlActivity; only tear down when this screen is truly finishing.
        if (isFinishing()) {
            mUserCancelled = true;
            ConnectedManager.getInstance().stop();
        }
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
