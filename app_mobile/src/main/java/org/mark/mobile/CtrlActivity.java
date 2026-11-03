package org.mark.mobile;

import android.Manifest;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.net.TrafficStats;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
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

import org.mark.mobile.utils.PreferUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.BuzzerCmd;
import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.LightCmd;
import org.mark.lib_unit_socket.bean.WheelCmd;
import org.mark.lib_unit_socket.bean.WheelRotateCmd;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ctrl.SpeedMixer;
import org.mark.mobile.ctrl.Throttle;
import org.mark.mobile.databinding.ActivityCtrlBinding;
import org.mark.mobile.databinding.ItemCyberActionBinding;
import org.mark.mobile.preview.LowLatencyRenderersFactory;
import org.mark.mobile.ui.cyber.CutShapeHelper;
import org.mark.mobile.ui.cyber.CyberSlider;
import org.mark.mobile.ui.cyber.OctagonJoystickView;
import org.mark.mobile.ui.cyber.ParallelogramDrawable;
import org.mark.mobile.ui.cyber.ScanlineDrawable;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Cyberpunk 2077 control page (spec §3). */
public class CtrlActivity extends AppCompatActivity {
    private static final String TAG = "CtrlActivity";
    public static final String EXTRA_HOST = "extra_host";
    public static final String EXTRA_PORT = "extra_port";
    /** Car closes a rejected 2nd controller ~500ms after accept; only save after surviving this. */
    private static final long SAVE_RECENT_DELAY_MS = 1000;
    private static final long SEND_INTERVAL_MS = 50;
    private static final int RSSI_WEAK = -75;
    private static final int REQ_STORAGE = 11;

    private ActivityCtrlBinding binding;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int mSpeedCurrent = WheelCmd.DEFAULT_SPEED;
    private float mStickPower;
    private boolean mLightOn;
    private boolean mDemo;

    private ParallelogramDrawable wifiBg;
    private TextView cellPower, cellSpeed, cellFps, cellBitrate;
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
        binding = ActivityCtrlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mDemo = BuildConfig.DEBUG && "demo".equals(getIntent().getStringExtra(MainActivity.EXTRA_DEBUG_STATE));

        binding.scanlineOverlay.setBackground(
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
        setLive(false);

        // Register first, then check: a disconnect that landed before registration is caught here.
        ConnectedManager.getInstance().addCallback(mClientMessageCallback);
        if (mDemo) applyDemo();
        checkStillConnected();
    }

    // ---------- top bar ----------
    private void setupTopBar() {
        binding.btnBack.setBackground(CutShapeHelper.states(
                CutShapeHelper.stroked(0, color(R.color.cp_yellow), dp(1), 0, 0, dp(11), 0),
                CutShapeHelper.stroked(0x26FCEE0A, color(R.color.cp_yellow), dp(1), 0, 0, dp(11), 0)));
        binding.btnBack.setOnClickListener(v -> finish());

        renderSubtitle();

        wifiBg = new ParallelogramDrawable(color(R.color.cp_cyan), dp(8));
        binding.textWifi.setBackground(wifiBg);
    }

    private void renderSubtitle() {
        ConnectedManager cm = ConnectedManager.getInstance();
        String host = mDemo ? "192.168.8.190" : cm.getConnectedHost();
        int port = mDemo ? 8010 : cm.getConnectedPort();
        boolean connected = mDemo || cm.isConnected();
        String addr = host == null || host.isEmpty() ? "--" : host + ":" + (port > 0 ? port : "--");
        binding.textDriveSub.setText(addr + (connected ? " · 已连接" : " · 未连接"));
        binding.textDriveSub.setTextColor(color(connected ? R.color.cp_cyan : R.color.cp_red));
    }

    private final Runnable rssiTick = new Runnable() {
        @Override public void run() {
            if (!mDemo) showRssi(org.mark.mobile.utils.WifiRssi.read(CtrlActivity.this));
            ui.postDelayed(this, 2000);
        }
    };

    private void showRssi(@Nullable Integer rssi) {
        boolean weak = rssi != null && rssi <= RSSI_WEAK;
        binding.textWifi.setText(rssi == null ? "WiFi --" : "WiFi " + rssi + "dBm");
        wifiBg.setColor(color(weak ? R.color.cp_red : R.color.cp_cyan));
        binding.textWifi.setTextColor(color(weak ? R.color.cp_white : R.color.cp_on_yellow));
    }

    // ---------- video ----------
    private void setupVideo() {
        binding.videoBox.setBackground(CutShapeHelper.panel(color(R.color.cp_panel), 0, dp(24), 0, 0));
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x40FF003C, 0x00000000, 0x4002D7F2});
        binding.videoTint.setBackground(tint);
        binding.videoScan.setBackground(new ScanlineDrawable(getResources(), 0x40000000));
        binding.videoCorner.setBackground(new CornerMask(color(R.color.cp_bg), dp(24)));
        binding.btnFullscreen.setBackground(CutShapeHelper.states(
                CutShapeHelper.panel(color(R.color.cp_yellow), 0, 0, dp(11), 0),
                CutShapeHelper.panel(color(R.color.cp_yellow_pressed), 0, 0, dp(11), 0)));
        binding.btnFullscreen.setOnClickListener(v -> openPreview());
        // Tapping the mini player also enters fullscreen preview (spec: entered from mini player).
        binding.playerSmall.setOnClickListener(v -> openPreview());
    }

    private static final int REQ_PREVIEW = 21;

    private void openPreview() {
        Intent intent = new Intent(this, org.mark.mobile.preview.H264Activity.class);
        intent.putExtra("host", ConnectedManager.getInstance().getConnectedHost());
        intent.putExtra(org.mark.mobile.preview.H264Activity.EXTRA_SERVO, binding.sliderServo.getValue());
        intent.putExtra(org.mark.mobile.preview.H264Activity.EXTRA_SPEED_LIMIT, binding.sliderSpeed.getValue());
        intent.putExtra(org.mark.mobile.preview.H264Activity.EXTRA_LIGHT, mLightOn);
        if (mDemo) intent.putExtra(MainActivity.EXTRA_DEBUG_STATE, "demo");
        startActivityForResult(intent, REQ_PREVIEW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PREVIEW && data != null) {
            mLightOn = data.getBooleanExtra(org.mark.mobile.preview.H264Activity.EXTRA_LIGHT, mLightOn);
            renderLight();
        }
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
        binding.playerSmall.setPlayer(player);
        player.addListener(new Player.EventListener() {
            @Override public void onPlaybackStateChanged(int state) {
                setLive(state == Player.STATE_READY);
            }
        });
        player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(uri)));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void releasePlayer() {
        binding.playerSmall.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        lastCounters = null;
        lastRxBytes = -1;
        setLive(false);
    }

    private void setLive(boolean live) {
        binding.textVideoState.setVisibility(live ? View.GONE : View.VISIBLE);
        binding.tagLive.setVisibility(live ? View.VISIBLE : View.GONE);
    }

    // ---------- telemetry ----------
    private void setupTelemetry() {
        cellPower = addCell(binding.telemetryRow, "摇杆功率", true);
        cellSpeed = addCell(binding.telemetryRow, "限速", true);
        cellFps = addCell(binding.telemetryRow, "视频帧率", true);
        cellBitrate = addCell(binding.telemetryRow, "码率", false);
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
        binding.cardGimbal.setBackground(
                CutShapeHelper.panel(color(R.color.cp_panel), 0, 0, dp(14), 0));
        binding.sliderServo.setListener((value, fromUser) -> {
            binding.textServo.setText(value + "%");
            servoThrottle.submit(value); // only on change, ≤ every 50ms, trailing flush once
        });
        binding.textServo.setText(binding.sliderServo.getValue() + "%");
        binding.btnRecenter.setBackground(CutShapeHelper.states(
                CutShapeHelper.stroked(0, color(R.color.cp_cyan), dp(1), 0, 0, dp(9), 0),
                CutShapeHelper.stroked(0x2602D7F2, color(R.color.cp_cyan), dp(1), 0, 0, dp(9), 0)));
        binding.btnRecenter.setOnClickListener(v -> binding.sliderServo.setValue(50, true));
    }

    // ---------- drive ----------
    private void setupDrive() {
        binding.joystick.setListener((angle, power) -> {
            if (angle < 0 || power <= 0.02f) {
                mStickPower = 0;
                wheelThrottle.sendNow("0,0");
            } else {
                mStickPower = power;
                int[] lr = SpeedMixer.roundSpeed(angle, Math.round(mSpeedCurrent * power));
                wheelThrottle.submit(lr[0] + "," + lr[1]);
            }
            refreshStickCells();
        });

        for (ImageView b : new ImageView[]{binding.btnLeft, binding.btnRight}) {
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
            rotateCmd.setRotate(v == binding.btnLeft ? WheelRotateCmd.Rotate.LEFT : WheelRotateCmd.Rotate.RIGHT);
            rotateCmd.setSpeed(action == MotionEvent.ACTION_DOWN ? mSpeedCurrent : 0);
            ConnectedManager.getInstance().sendObject(rotateCmd, CmdConstant.WHEEL_ROTATE);
            return false;
        }
    };

    private void setupSpeed() {
        int saved = PreferUtils.getInstance(this).getSpeed(WheelCmd.DEFAULT_SPEED);
        if (saved <= 0 || saved > 100) saved = WheelCmd.DEFAULT_SPEED;
        mSpeedCurrent = saved;
        binding.sliderSpeed.setValue(saved, false);
        binding.textSpeed.setText(saved + "%");
        binding.sliderSpeed.setListener((value, fromUser) -> {
            mSpeedCurrent = value;
            binding.textSpeed.setText(value + "%");
            PreferUtils.getInstance(this).saveSpeed(value);
            refreshStickCells();
        });
        refreshStickCells();
    }

    // ---------- actions ----------
    private void setupActions() {
        bindAction(binding.btnLight, R.drawable.ic_light, "车灯 关");
        renderLight();
        binding.btnLight.getRoot().setOnClickListener(v -> {
            mLightOn = !mLightOn;
            LightCmd lightCmd = new LightCmd();
            lightCmd.setEnable(mLightOn);
            ConnectedManager.getInstance().sendObject(lightCmd, CmdConstant.LIGHT);
            renderLight();
        });

        bindAction(binding.btnHorn, R.drawable.ic_buzzer, "鸣笛 按住");
        binding.btnHorn.getRoot().setBackground(CutShapeHelper.states(actionBg(color(R.color.cp_panel)), actionBg(color(R.color.cp_red))));
        binding.btnHorn.getRoot().setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                ui.removeCallbacks(hornTick);
                ui.post(hornTick);
                setActionColors(binding.btnHorn, color(R.color.cp_white), false);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                // Car beeps 500ms per di:true; just stop resending (di:false is a no-op).
                ui.removeCallbacks(hornTick);
                setActionColors(binding.btnHorn, color(R.color.cp_text_dim), false);
            }
            return false;
        });

        bindAction(binding.btnSnapshot, R.drawable.ic_snapshot, "截图");
        binding.btnSnapshot.getRoot().setBackground(CutShapeHelper.states(actionBg(color(R.color.cp_panel)), actionBg(0xFF22222E)));
        binding.btnSnapshot.getRoot().setOnClickListener(v -> takeSnapshot());
    }

    private static final long HORN_REPEAT_MS = 400;
    private final Runnable hornTick = new Runnable() {
        @Override public void run() {
            ConnectedManager.getInstance().sendObject(new BuzzerCmd(true), CmdConstant.BUZZER);
            ui.postDelayed(this, HORN_REPEAT_MS);
        }
    };

    private Drawable actionBg(int fill) {
        return new BottomEdgeCut(fill, color(R.color.cp_action_edge), dp(2), dp(15), dp(14));
    }

    private void bindAction(ItemCyberActionBinding item, int icon, String text) {
        item.actionIcon.setImageResource(icon);
        item.actionText.setText(text);
        setActionColors(item, color(R.color.cp_text_dim), false);
    }

    private void setActionColors(ItemCyberActionBinding item, int c, boolean bold) {
        item.actionIcon.setImageTintList(ColorStateList.valueOf(c));
        item.actionText.setTextColor(c);
        item.actionText.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void renderLight() {
        binding.btnLight.actionText.setText(mLightOn ? "车灯 开" : "车灯 关");
        binding.btnLight.getRoot().setBackground(actionBg(color(mLightOn ? R.color.cp_yellow : R.color.cp_panel)));
        setActionColors(binding.btnLight, color(mLightOn ? R.color.cp_on_yellow : R.color.cp_text_dim), mLightOn);
    }

    // ---------- screenshot ----------
    private void takeSnapshot() {
        View surface = binding.playerSmall.getVideoSurfaceView();
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
        Uri saved = org.mark.mobile.utils.GallerySaver.save(this, frame);
        Toast.makeText(this, saved != null ? "已保存到相册" : "截图保存失败", Toast.LENGTH_SHORT).show();
    }

    // ---------- debug demo (screenshots only) ----------
    private void applyDemo() {
        binding.sliderServo.setValue(65, false);
        binding.sliderSpeed.setValue(72, false);
        mStickPower = 0.72f;
        refreshStickCells();
        setCell(cellFps, "30", "FPS");
        setCell(cellBitrate, "4.2", "Mbps");
        showRssi(-52);
        mLightOn = true;
        renderLight();
        binding.tagLive.setVisibility(View.VISIBLE);
    }

    // ---------- lifecycle / connection ----------
    @Override
    protected void onResume() {
        super.onResume();
        checkStillConnected();
        if (!mReturning && !mRecentSaved) {
            ui.removeCallbacks(saveRecent);
            ui.postDelayed(saveRecent, SAVE_RECENT_DELAY_MS);
        }
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(saveRecent);
        super.onPause();
    }

    private boolean mRecentSaved;
    private final Runnable saveRecent = new Runnable() {
        @Override public void run() {
            if (mReturning || mDemo || !ConnectedManager.getInstance().isConnected()) return;
            String host = getIntent().getStringExtra(EXTRA_HOST);
            int port = getIntent().getIntExtra(EXTRA_PORT, 0);
            if (host == null || host.isEmpty() || port <= 0) return;
            PreferUtils p = PreferUtils.getInstance(CtrlActivity.this);
            p.add(host, port);
            p.save(host, port);
            mRecentSaved = true;
            Log.d(TAG, "recent saved " + host + ":" + port);
        }
    };

    /** Car closes a rejected 2nd controller ~500ms after accept; we may open after that. */
    private void checkStillConnected() {
        if (!mDemo && !ConnectedManager.getInstance().isConnected()) {
            backToConnectRefused();
        }
    }

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
        ui.removeCallbacks(hornTick);
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
            runOnUiThread(() -> renderSubtitle());
            if (status == Status.NO_CONNECT) {
                backToConnectRefused();
            }
        }
    };

    private volatile boolean mReturning;

    /** Car RSTs a second controller after TCP connect; return to Connect page in refused state. */
    private void backToConnectRefused() {
        // Idempotent: onCreate + onResume checks and socket callbacks may all fire.
        // Atomic claim (callbacks arrive on the socket thread), then finish on the UI thread.
        synchronized (this) {
            if (mReturning || isFinishing()) {
                return;
            }
            mReturning = true;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ui.removeCallbacks(saveRecent);
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
        ui.removeCallbacks(saveRecent);
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
