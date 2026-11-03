package org.mark.mobile;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.net.TrafficStats;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;

import org.mark.base.PreferUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.BuzzerCmd;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.LightCmd;
import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.lib_unit_socket.bean.WheelRotateCmd;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ctrl.KeyIndex;
import org.mark.mobile.ctrl.RockerListener;
import org.mark.mobile.ctrl.Throttle;
import org.mark.mobile.preview.LowLatencyRenderersFactory;
import org.mark.mobile.ui.cyber.CutShapeHelper;
import org.mark.mobile.ui.cyber.CyberSlider;
import org.mark.mobile.ui.cyber.OctagonJoystickView;
import org.mark.mobile.ui.cyber.ParallelogramDrawable;
import org.mark.mobile.ui.cyber.ScanlineDrawable;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Cyberpunk 2077 control page (spec §3). */
public class CtrlActivity extends AppCompatActivity {
    private static final String TAG = "CtrlActivity";
    private static final long SEND_INTERVAL_MS = 50;
    private static final int RSSI_WEAK = -75;
    private static final int REQ_STORAGE = 11;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private int mSpeedCurrent = WheelCmd.DEFAULT_SPEED;
    private float mStickPower;
    private boolean mLightOn;
    private boolean mDemo;

    private TextView textWifi;
    private ParallelogramDrawable wifiBg;
    private TextView cellPower, cellSpeed, cellFps, cellBitrate;
    private TextView textServo, textSpeed, textVideoState;
    private CyberSlider sliderServo, sliderSpeed;
    private View btnLight;
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private DecoderCounters lastCounters;
    private int lastFrames;
    private long lastStatsTime;
    private long lastRxBytes = -1;

    private Throttle<Integer> servoThrottle;
    private Throttle<String> wheelThrottle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ctrl);
        mDemo = BuildConfig.DEBUG && "demo".equals(getIntent().getStringExtra(MainActivity.EXTRA_DEBUG_STATE));

        findViewById(R.id.scanline_overlay).setBackground(
                new ScanlineDrawable(getResources(), color(R.color.scanline)));

        servoThrottle = new Throttle<>(SEND_INTERVAL_MS, new Throttle.Sink<Integer>() {
            @Override public void send(Integer v) {
                Log.d(TAG, "servo " + v);
                ConnectedManager.getInstance().sendObject(new CameraServoCmd(v), CmdConstant.CAMERA_SERVO);
            }
        });
        wheelThrottle = new Throttle<>(SEND_INTERVAL_MS, new Throttle.Sink<String>() {
            @Override public void send(String v) {
                String[] lr = v.split(",");
                WheelCmd cmd = new WheelCmd(Integer.parseInt(lr[0]), Integer.parseInt(lr[1]));
                ConnectedManager.getInstance().sendObject(cmd, CmdConstant.WHEEL);
            }
        });

        setupTopBar();
        setupVideo();
        setupTelemetry();
        setupGimbal();
        setupDrive();
        setupSpeed();
        setupActions();

        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
        if (mDemo) applyDemo();
    }

    // ---------- top bar ----------
    private void setupTopBar() {
        View back = findViewById(R.id.btn_back);
        back.setBackground(CutShapeHelper.states(
                CutShapeHelper.stroked(0, color(R.color.cp_yellow), dp(1), 0, 0, dp(11), 0),
                CutShapeHelper.stroked(0x26FCEE0A, color(R.color.cp_yellow), dp(1), 0, 0, dp(11), 0)));
        back.setOnClickListener(v -> finish());

        String host = ConnectedManager.getInstance().getConnectedHost();
        String port = new org.mark.mobile.utils.PreferUtils(this).getPort();
        if (host == null) host = "--";
        if ("0".equals(port)) port = "8010";
        ((TextView) findViewById(R.id.text_drive_sub)).setText(host + ":" + port + " · 已连接");

        textWifi = findViewById(R.id.text_wifi);
        wifiBg = new ParallelogramDrawable(color(R.color.cp_cyan), dp(8));
        textWifi.setBackground(wifiBg);
    }

    private final Runnable rssiTick = new Runnable() {
        @Override public void run() {
            if (!mDemo) showRssi(readRssi());
            ui.postDelayed(this, 2000);
        }
    };

    /** Phone's own Wi-Fi RSSI; null when unavailable (no Wi-Fi / permission). */
    @Nullable
    private Integer readRssi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return null;
            int rssi = info.getRssi();
            return rssi <= -127 || rssi >= 0 ? null : rssi;
        } catch (SecurityException e) {
            return null;
        }
    }

    private void showRssi(@Nullable Integer rssi) {
        boolean weak = rssi != null && rssi <= RSSI_WEAK;
        textWifi.setText(rssi == null ? "WiFi --" : "WiFi " + rssi + "dBm");
        wifiBg.setColor(color(weak ? R.color.cp_red : R.color.cp_cyan));
        textWifi.setTextColor(color(weak ? R.color.cp_white : R.color.cp_on_yellow));
    }

    // ---------- video ----------
    private void setupVideo() {
        View box = findViewById(R.id.video_box);
        box.setBackground(CutShapeHelper.panel(color(R.color.cp_panel), 0, dp(24), 0, 0));
        playerView = findViewById(R.id.player_small);
        textVideoState = findViewById(R.id.text_video_state);
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x40FF003C, 0x00000000, 0x4002D7F2});
        findViewById(R.id.video_tint).setBackground(tint);
        findViewById(R.id.video_scan).setBackground(new ScanlineDrawable(getResources(), 0x40000000));
        findViewById(R.id.video_corner).setBackground(new CornerMask(color(R.color.cp_bg), dp(24)));
        View full = findViewById(R.id.btn_fullscreen);
        full.setBackground(CutShapeHelper.states(
                CutShapeHelper.panel(color(R.color.cp_yellow), 0, 0, dp(11), 0),
                CutShapeHelper.panel(color(R.color.cp_yellow_pressed), 0, 0, dp(11), 0)));
        full.setOnClickListener(v -> {
            Intent intent = new Intent(this, org.mark.mobile.preview.H264Activity.class);
            intent.putExtra("host", ConnectedManager.getInstance().getConnectedHost());
            startActivity(intent);
        });
    }

    private void startPlayer() {
        String host = ConnectedManager.getInstance().getConnectedHost();
        if (host == null || host.trim().isEmpty() || mDemo) return;
        Uri uri = new Uri.Builder().scheme("rtsp").encodedAuthority(host + ":8554").appendPath("csi").build();
        DefaultLoadControl buffering = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(75, 200, 50, 75)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new SimpleExoPlayer.Builder(this, new LowLatencyRenderersFactory(this))
                .setLoadControl(buffering).build();
        player.setVolume(0f);
        playerView.setPlayer(player);
        player.addListener(new Player.EventListener() {
            @Override public void onPlaybackStateChanged(int state) {
                textVideoState.setVisibility(state == Player.STATE_READY ? View.GONE : View.VISIBLE);
            }
        });
        player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(uri)));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void releasePlayer() {
        playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        lastCounters = null;
        lastRxBytes = -1;
        textVideoState.setVisibility(View.VISIBLE);
    }

    // ---------- telemetry ----------
    private void setupTelemetry() {
        LinearLayout row = findViewById(R.id.telemetry_row);
        cellPower = addCell(row, "摇杆功率", true);
        cellSpeed = addCell(row, "限速", true);
        cellFps = addCell(row, "视频帧率", true);
        cellBitrate = addCell(row, "码率", false);
    }

    private TextView addCell(LinearLayout row, String label, boolean gap) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(8), dp(6) + dp(2), dp(8), dp(6));
        cell.setBackground(new TopBarPanel(color(R.color.cp_panel), color(R.color.cp_cyan), dp(2)));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(color(R.color.cp_cyan));
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        TextView v = new TextView(this);
        v.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.orbitron_bold));
        v.setTextColor(color(R.color.cp_yellow));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setSingleLine(true);
        v.setText("--");
        cell.addView(l);
        cell.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        if (gap) lp.setMarginEnd(dp(6));
        row.addView(cell, lp);
        return v;
    }

    private static CharSequence valueUnit(String value, String unit, int unitColor, int unitPx) {
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (unit != null) {
            int s = sb.length();
            sb.append(" ").append(unit);
            sb.setSpan(new AbsoluteSizeSpan(unitPx), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(unitColor), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void setCell(TextView cell, @Nullable String value, String unit) {
        if (value == null) {
            cell.setText("--");
        } else {
            cell.setText(valueUnit(value, unit, 0xB3FCEE0A, sp(10)));
        }
    }

    private void refreshStickCells() {
        setCell(cellPower, String.valueOf(Math.round(mStickPower * 100)), "%");
        setCell(cellSpeed, String.valueOf(mSpeedCurrent), "%");
    }

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            if (!mDemo) updateVideoStats();
            ui.postDelayed(this, 1000);
        }
    };

    private void updateVideoStats() {
        boolean playing = player != null && player.getPlaybackState() == Player.STATE_READY;
        long now = SystemClock.elapsedRealtime();
        if (!playing) {
            setCell(cellFps, null, null);
            setCell(cellBitrate, null, null);
            lastCounters = null;
            lastRxBytes = -1;
            lastStatsTime = now;
            return;
        }
        // FPS: rendered output buffers delta (DecoderCounters)
        DecoderCounters c = player.getVideoDecoderCounters();
        String fps = null;
        if (c != null) {
            c.ensureUpdated();
            if (c == lastCounters && now > lastStatsTime) {
                fps = String.valueOf(Math.round((c.renderedOutputBufferCount - lastFrames) * 1000f / (now - lastStatsTime)));
            }
            lastFrames = c.renderedOutputBufferCount;
        }
        lastCounters = c;
        // Bitrate: app UID received bytes/s (RTSP stream dominates traffic)
        long rx = TrafficStats.getUidRxBytes(Process.myUid());
        String mbps = null;
        if (rx != TrafficStats.UNSUPPORTED && lastRxBytes >= 0 && now > lastStatsTime) {
            double bits = (rx - lastRxBytes) * 8.0 * 1000.0 / (now - lastStatsTime);
            mbps = String.format(Locale.US, "%.1f", bits / 1_000_000.0);
        }
        lastRxBytes = rx;
        lastStatsTime = now;
        setCell(cellFps, fps, "FPS");
        setCell(cellBitrate, mbps, "Mbps");
    }

    // ---------- gimbal ----------
    private void setupGimbal() {
        findViewById(R.id.card_gimbal).setBackground(
                CutShapeHelper.panel(color(R.color.cp_panel), 0, 0, dp(14), 0));
        textServo = findViewById(R.id.text_servo);
        sliderServo = findViewById(R.id.slider_servo);
        sliderServo.setListener(new CyberSlider.Listener() {
            @Override public void onValueChanged(int value, boolean fromUser) {
                textServo.setText(value + "%");
                servoThrottle.submit(value); // only on change, ≤ every 50ms, trailing flush once
            }
            @Override public void onStartTracking() { }
            @Override public void onStopTracking() { }
        });
        textServo.setText(sliderServo.getValue() + "%");
        View recenter = findViewById(R.id.btn_recenter);
        recenter.setBackground(CutShapeHelper.states(
                CutShapeHelper.stroked(0, color(R.color.cp_cyan), dp(1), 0, 0, dp(9), 0),
                CutShapeHelper.stroked(0x2602D7F2, color(R.color.cp_cyan), dp(1), 0, 0, dp(9), 0)));
        recenter.setOnClickListener(v -> sliderServo.setValue(50, true));
    }

    // ---------- drive ----------
    private void setupDrive() {
        OctagonJoystickView joystick = findViewById(R.id.joystick);
        joystick.setListener((angle, power) -> {
            if (angle < 0 || power <= 0.02f) {
                mStickPower = 0;
                wheelThrottle.sendNow("0,0");
            } else {
                mStickPower = power;
                int[] lr = RockerListener.roundSpeed(angle, Math.round(mSpeedCurrent * power));
                wheelThrottle.submit(lr[0] + "," + lr[1]);
            }
            refreshStickCells();
        });

        for (int id : new int[]{R.id.btnLeft, R.id.btnRight}) {
            ImageView b = findViewById(id);
            b.setBackground(CutShapeHelper.states(
                    CutShapeHelper.stroked(color(R.color.cyan_08), color(R.color.cp_cyan), dp(2), 0, 0, dp(16), 0),
                    CutShapeHelper.stroked(color(R.color.cp_cyan), color(R.color.cp_cyan), dp(2), 0, 0, dp(16), 0)));
            b.setImageTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_pressed}, {}},
                    new int[]{color(R.color.cp_on_yellow), color(R.color.cp_cyan)}));
            b.setClickable(true);
            b.setOnTouchListener(rotateTouchListener);
        }
    }

    private final View.OnTouchListener rotateTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP
                    && action != MotionEvent.ACTION_CANCEL) {
                return false;
            }
            WheelRotateCmd rotateCmd = new WheelRotateCmd();
            rotateCmd.setRotate(v.getId() == R.id.btnLeft ? WheelRotateCmd.Rotate.LEFT : WheelRotateCmd.Rotate.RIGHT);
            rotateCmd.setSpeed(action == MotionEvent.ACTION_DOWN ? mSpeedCurrent : 0);
            ConnectedManager.getInstance().sendObject(rotateCmd, CmdConstant.WHEEL_ROTATE);
            return false;
        }
    };

    private void setupSpeed() {
        textSpeed = findViewById(R.id.text_speed);
        sliderSpeed = findViewById(R.id.slider_speed);
        PreferUtils.getInstance().init(this);
        int saved = PreferUtils.getInstance().get(KeyIndex.SPEED);
        if (saved <= 0 || saved > 100) saved = WheelCmd.DEFAULT_SPEED;
        mSpeedCurrent = saved;
        sliderSpeed.setValue(saved, false);
        textSpeed.setText(saved + "%");
        sliderSpeed.setListener(new CyberSlider.Listener() {
            @Override public void onValueChanged(int value, boolean fromUser) {
                mSpeedCurrent = value;
                textSpeed.setText(value + "%");
                PreferUtils.getInstance().put(KeyIndex.SPEED, value);
                refreshStickCells();
            }
            @Override public void onStartTracking() { }
            @Override public void onStopTracking() { }
        });
        refreshStickCells();
    }

    // ---------- actions ----------
    private void setupActions() {
        btnLight = findViewById(R.id.btn_light);
        bindAction(btnLight, R.drawable.ic_light, "车灯 关");
        renderLight();
        btnLight.setOnClickListener(v -> {
            mLightOn = !mLightOn;
            LightCmd lightCmd = new LightCmd();
            lightCmd.setEnable(mLightOn);
            ConnectedManager.getInstance().sendObject(lightCmd, CmdConstant.LIGHT);
            renderLight();
        });

        View horn = findViewById(R.id.btn_horn);
        bindAction(horn, R.drawable.ic_buzzer, "鸣笛 按住");
        horn.setBackground(CutShapeHelper.states(actionBg(color(R.color.cp_panel)), actionBg(color(R.color.cp_red))));
        horn.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                ConnectedManager.getInstance().sendObject(new BuzzerCmd(true), CmdConstant.BUZZER);
                setActionColors(v, color(R.color.cp_white), false);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                ConnectedManager.getInstance().sendObject(new BuzzerCmd(false), CmdConstant.BUZZER);
                setActionColors(v, color(R.color.cp_text_dim), false);
            }
            return false;
        });

        View snap = findViewById(R.id.btn_snapshot);
        bindAction(snap, R.drawable.ic_snapshot, "截图");
        snap.setBackground(CutShapeHelper.states(actionBg(color(R.color.cp_panel)), actionBg(0xFF22222E)));
        snap.setOnClickListener(v -> takeSnapshot());
    }

    private Drawable actionBg(int fill) {
        return new BottomEdgeCut(fill, color(R.color.cp_action_edge), dp(2), dp(15), dp(14));
    }

    private void bindAction(View root, int icon, String text) {
        ((ImageView) root.findViewById(R.id.action_icon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.action_text)).setText(text);
        setActionColors(root, color(R.color.cp_text_dim), false);
    }

    private void setActionColors(View root, int c, boolean bold) {
        ((ImageView) root.findViewById(R.id.action_icon)).setImageTintList(ColorStateList.valueOf(c));
        TextView t = root.findViewById(R.id.action_text);
        t.setTextColor(c);
        t.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void renderLight() {
        ((TextView) btnLight.findViewById(R.id.action_text)).setText(mLightOn ? "车灯 开" : "车灯 关");
        btnLight.setBackground(actionBg(color(mLightOn ? R.color.cp_yellow : R.color.cp_panel)));
        setActionColors(btnLight, color(mLightOn ? R.color.cp_on_yellow : R.color.cp_text_dim), mLightOn);
    }

    // ---------- screenshot ----------
    private void takeSnapshot() {
        View surface = playerView.getVideoSurfaceView();
        Bitmap frame = null;
        if (player != null && player.getPlaybackState() == Player.STATE_READY && surface instanceof TextureView) {
            frame = ((TextureView) surface).getBitmap();
        }
        if (frame == null) {
            Toast.makeText(this, "暂无视频画面，无法截图", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            Toast.makeText(this, "需要存储权限，授权后请再点一次截图", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri saved = saveToGallery(frame);
        Toast.makeText(this, saved != null ? "已保存到相册" : "截图保存失败", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private Uri saveToGallery(Bitmap bmp) {
        String name = "ThingsHello_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put("relative_path", "Pictures/ThingsHello"); // MediaStore.MediaColumns.RELATIVE_PATH
            v.put("is_pending", 1);
        }
        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null || !bmp.compress(Bitmap.CompressFormat.JPEG, 92, os)) throw new java.io.IOException("write");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put("is_pending", 0);
                cr.update(uri, done, null, null);
            }
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "snapshot", e);
            if (uri != null) cr.delete(uri, null, null);
            return null;
        }
    }

    // ---------- debug demo (screenshots only) ----------
    private void applyDemo() {
        sliderServo.setValue(65, false);
        sliderSpeed.setValue(72, false);
        mStickPower = 0.72f;
        refreshStickCells();
        setCell(cellFps, "30", "FPS");
        setCell(cellBitrate, "4.2", "Mbps");
        showRssi(-52);
        mLightOn = true;
        renderLight();
    }

    // ---------- lifecycle / connection ----------
    @Override
    protected void onStart() {
        super.onStart();
        startPlayer();
        ui.post(rssiTick);
        ui.post(statsTick);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(rssiTick);
        ui.removeCallbacks(statsTick);
        // Stop motion if we leave mid-drive; no servo resend (car releases servo itself).
        servoThrottle.cancel();
        wheelThrottle.sendNow("0,0");
        releasePlayer();
        super.onStop();
    }

    ClientMessageCallback mClientMessageCallback = new ClientMessageCallback() {
        @Override
        public void onReceiveMessage(final byte[] message, int type) {
        }

        @Override
        public void onExceptionToReOpen(@NonNull Exception e) {
            backToConnectRefused();
        }

        @Override
        public void onLogMessage(final String message, @Nullable Exception e) {
        }

        @Override
        public void onStatusChange(@NonNull Status status) {
            if (status == Status.NO_CONNECT) {
                backToConnectRefused();
            }
        }
    };

    private volatile boolean mReturning;

    /** Car RSTs a second controller after TCP connect; return to Connect page in refused state. */
    private void backToConnectRefused() {
        if (mReturning) {
            return;
        }
        mReturning = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(CtrlActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_REFUSED, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        ConnectedManager.getInstance().removeCallback(mClientMessageCallback);
        super.onDestroy();
    }

    // ---------- helpers ----------
    private int color(int res) { return ContextCompat.getColor(this, res); }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private int sp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics()));
    }

    /** Covers the top-right triangle with page bg to fake a TR cut over the video surface. */
    private static final class CornerMask extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cut;
        CornerMask(int c, float cut) { p.setColor(c); this.cut = cut; }
        @Override public void draw(@NonNull Canvas c) {
            Path path = new Path();
            float r = getBounds().right, t = getBounds().top;
            path.moveTo(r - cut, t); path.lineTo(r, t); path.lineTo(r, t + cut); path.close();
            c.drawPath(path, p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(@Nullable ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Telemetry cell: panel fill + 2dp cyan top edge. */
    private static final class TopBarPanel extends Drawable {
        private final Paint fill = new Paint(), bar = new Paint();
        private final float h;
        TopBarPanel(int f, int b, float h) { fill.setColor(f); bar.setColor(b); this.h = h; }
        @Override public void draw(@NonNull Canvas c) {
            c.drawRect(getBounds(), fill);
            c.drawRect(getBounds().left, getBounds().top, getBounds().right, getBounds().top + h, bar);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(@Nullable ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Action key: fill, BR cut, 2dp bottom edge (#333). */
    private static final class BottomEdgeCut extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float h, cutW, cutH;
        BottomEdgeCut(int f, int e, float h, float cutW, float cutH) {
            fill.setColor(f); edge.setColor(e); this.h = h; this.cutW = cutW; this.cutH = cutH;
        }
        @Override public void draw(@NonNull Canvas c) {
            float l = getBounds().left, t = getBounds().top, r = getBounds().right, b = getBounds().bottom;
            Path p = new Path();
            p.moveTo(l, t); p.lineTo(r, t); p.lineTo(r, b - cutH); p.lineTo(r - cutW, b); p.lineTo(l, b); p.close();
            c.drawPath(p, fill);
            c.drawRect(l, b - h, r - cutW, b, edge);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(@Nullable ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
