package org.mark.mobile.preview;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.video.VideoSize;

import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.LightCmd;
import org.mark.mobile.BuildConfig;
import org.mark.mobile.MainActivity;
import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.ui.cyber.CutShapeHelper;
import org.mark.mobile.ui.cyber.ScanlineDrawable;
import org.mark.mobile.utils.GallerySaver;
import org.mark.mobile.utils.WifiRssi;

import java.util.Locale;

/** Fullscreen CSI preview (2077 HUD). ExoPlayer handles RTSP/H264 over TCP. */
public final class H264Activity extends AppCompatActivity {
    public static final String EXTRA_SERVO = "servo";
    public static final String EXTRA_POWER = "power";
    public static final String EXTRA_LIGHT = "light";
    private static final long HUD_AUTO_HIDE_MS = 4000;

    private PlayerView video;
    private SimpleExoPlayer player;
    private View hud, tagRec, hint, hudRoot;
    private TextView noSignal, pillStream, pillBuf, pillWifi, pillRate, pillServo, pillPower;
    private ImageView btnLight;
    private boolean demo, lightOn, hudVisible = true;
    private long lastStatsTime, lastRxBytes = -1;
    private int lastRenderedFrames;
    private DecoderCounters lastCounters;
    private float appliedSpeed = 1.0f;
    private Uri stream;
    private final Handler diagnostics = new Handler(Looper.getMainLooper());

    private final Runnable catchUp = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            float next = 1.0f;
            if (player.getPlaybackState() == Player.STATE_READY) {
                long bufferedMs = player.getTotalBufferedDuration();
                if (bufferedMs > 350) next = 1.25f;
                else if (bufferedMs > 140) next = 1.15f;
                else if (bufferedMs > 75) next = 1.05f;
                else if (bufferedMs > 45) next = appliedSpeed > 1.0f ? 1.05f : 1.0f;
            }
            if (next != appliedSpeed) {
                appliedSpeed = next;
                player.setPlaybackSpeed(next);
            }
            diagnostics.postDelayed(this, 200);
        }
    };
    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateHud();
            diagnostics.postDelayed(this, 1000);
        }
    };
    /** If the control TCP link drops while here, go back; CtrlActivity.onResume shows refused. */
    private final Runnable linkWatch = new Runnable() {
        @Override public void run() {
            if (!demo && !ConnectedManager.getInstance().isConnected()) { finishWithResult(); return; }
            diagnostics.postDelayed(this, 500);
        }
    };
    private final Runnable autoHide = () -> setHudVisible(false);
    private final Runnable hideHint = () -> hint.animate().alpha(0f).setDuration(200).start();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_preview_cyber);
        demo = BuildConfig.DEBUG && getIntent().getStringExtra(MainActivity.EXTRA_DEBUG_STATE) != null;
        lightOn = getIntent().getBooleanExtra(EXTRA_LIGHT, false);

        video = findViewById(R.id.player_full);
        hud = findViewById(R.id.hud);
        hudRoot = findViewById(R.id.hud_root);
        tagRec = findViewById(R.id.tag_rec);
        hint = findViewById(R.id.text_hint);
        noSignal = findViewById(R.id.text_no_signal);
        pillStream = findViewById(R.id.pill_stream);
        pillBuf = findViewById(R.id.pill_buf);
        pillWifi = findViewById(R.id.pill_wifi);
        pillRate = findViewById(R.id.pill_rate);
        pillServo = findViewById(R.id.pill_servo);
        pillPower = findViewById(R.id.pill_power);
        btnLight = findViewById(R.id.btn_light_pv);

        int yellow = color(R.color.cp_yellow);
        int pillBg = color(R.color.scrim_70);
        for (TextView p : new TextView[]{pillStream, pillBuf, pillWifi, pillRate, pillServo, pillPower}) {
            CutShapeHelper.applyWithLeftBar(p, pillBg, color(R.color.cp_cyan), dp(2), 0, 0, 0, 0);
        }
        hint.setBackground(CutShapeHelper.panel(pillBg, 0, 0, 0, 0));
        tagRec.setBackground(CutShapeHelper.panel(color(R.color.cp_red), 0, 0, 0, 0));

        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x2EFF003C, 0x00000000, 0x2E02D7F2});
        findViewById(R.id.overlay_tint).setBackground(tint);
        findViewById(R.id.overlay_scan).setBackground(new ScanlineDrawable(getResources(), 0x26000000));
        findViewById(R.id.overlay_frame).setBackground(
                CutShapeHelper.stroked(0x00000000, (yellow & 0x00FFFFFF) | 0x99000000, dp(2), 0, dp(30), 0, dp(30)));

        View back = findViewById(R.id.btn_back_pv);
        back.setBackground(CutShapeHelper.stroked(pillBg, yellow, dp(1), 0, 0, dp(10), 0));
        back.setOnClickListener(v -> finishWithResult());
        View snap = findViewById(R.id.btn_snap_pv);
        snap.setBackground(CutShapeHelper.stroked(pillBg, yellow, dp(1), 0, 0, dp(10), 0));
        snap.setOnClickListener(v -> { takeSnapshot(); bumpHud(); });
        btnLight.setOnClickListener(v -> {
            lightOn = !lightOn;
            if (!demo) {
                LightCmd cmd = new LightCmd();
                cmd.setEnable(lightOn);
                ConnectedManager.getInstance().sendObject(cmd, CmdConstant.LIGHT);
            }
            renderLight();
            bumpHud();
        });
        renderLight();

        pillServo.setText(String.format(Locale.US, "云台 %d%%", getIntent().getIntExtra(EXTRA_SERVO, 50)));
        pillPower.setText(String.format(Locale.US, "功率 %d%%", getIntent().getIntExtra(EXTRA_POWER, 0)));

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (!demo && player != null && player.getPlayerError() != null) {
                    noSignal.setText("NO SIGNAL");
                    play();
                }
                setHudVisible(!hudVisible);
                return true;
            }
        });
        View root = findViewById(R.id.preview_root);
        root.setOnTouchListener((v, e) -> gd.onTouchEvent(e));

        // keep HUD clear of the notch / rounded corners
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = 0, t = 0, r = 0, b = 0;
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout c = insets.getDisplayCutout();
                if (c != null) { l = c.getSafeInsetLeft(); t = c.getSafeInsetTop(); r = c.getSafeInsetRight(); b = c.getSafeInsetBottom(); }
            }
            hudRoot.setPadding(l, t, r, b);
            return insets;
        });

        String host = getIntent().getStringExtra("host");
        if (host != null && !host.trim().isEmpty()) {
            stream = new Uri.Builder().scheme("rtsp").encodedAuthority(host.trim() + ":8554").appendPath("csi").build();
        }
        setLive(false);
        if (demo) applyDemo();
        diagnostics.postDelayed(hideHint, 3000);
        bumpHud();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // ---------- HUD ----------
    private void setHudVisible(boolean show) {
        hudVisible = show;
        hud.animate().cancel();
        if (show) hud.setVisibility(View.VISIBLE);
        hud.animate().alpha(show ? 1f : 0f).setDuration(200)
                .withEndAction(() -> { if (!hudVisible) hud.setVisibility(View.INVISIBLE); }).start();
        diagnostics.removeCallbacks(autoHide);
        if (show && !demo) diagnostics.postDelayed(autoHide, HUD_AUTO_HIDE_MS);
    }

    private void bumpHud() {
        if (!hudVisible) setHudVisible(true);
        diagnostics.removeCallbacks(autoHide);
        if (!demo) diagnostics.postDelayed(autoHide, HUD_AUTO_HIDE_MS);
    }

    private void renderLight() {
        int yellow = color(R.color.cp_yellow);
        btnLight.setBackground(lightOn
                ? CutShapeHelper.stroked(yellow, yellow, dp(1), 0, 0, dp(10), 0)
                : CutShapeHelper.stroked(color(R.color.scrim_70), yellow, dp(1), 0, 0, dp(10), 0));
        btnLight.setColorFilter(lightOn ? color(R.color.cp_bg) : yellow);
        ((ImageView) findViewById(R.id.btn_snap_pv)).setColorFilter(yellow);
    }

    private void setLive(boolean live) {
        tagRec.setVisibility(live ? View.VISIBLE : View.GONE);
        noSignal.setVisibility(live ? View.GONE : View.VISIBLE);
    }

    private void updateHud() {
        if (demo) return;
        Integer rssi = WifiRssi.read(this);
        pillWifi.setText(rssi == null ? "手机 WiFi --" : "手机 WiFi " + rssi + "dBm");
        long now = SystemClock.elapsedRealtime();
        String fps = null, rate = "--";
        long rx = TrafficStats.getUidRxBytes(Process.myUid());
        if (rx != TrafficStats.UNSUPPORTED && lastRxBytes >= 0 && now > lastStatsTime && player != null) {
            double mbps = (rx - lastRxBytes) * 8.0 / 1e6 / ((now - lastStatsTime) / 1000.0);
            rate = String.format(Locale.US, "%.1f", mbps);
        }
        if (player != null) {
            DecoderCounters c = player.getVideoDecoderCounters();
            if (c != null) {
                c.ensureUpdated();
                int frames = c.renderedOutputBufferCount;
                if (c == lastCounters && now > lastStatsTime && frames >= lastRenderedFrames) {
                    fps = String.valueOf(Math.round((frames - lastRenderedFrames) * 1000.0 / (now - lastStatsTime)));
                }
                lastRenderedFrames = frames;
            }
            lastCounters = c;
            boolean ready = player.getPlaybackState() == Player.STATE_READY;
            pillBuf.setText(ready ? "BUF " + player.getTotalBufferedDuration() + "ms" : "BUF --");
            VideoSize s = player.getVideoSize();
            String res = s.width > 0 ? s.width + "×" + s.height : "--";
            pillStream.setText("CSI · " + res + " · " + (fps == null ? "--" : fps) + "FPS · RTSP");
        } else {
            pillBuf.setText("BUF --");
            pillStream.setText("CSI · -- · --FPS · RTSP");
        }
        pillRate.setText(rate + "Mbps");
        lastRxBytes = rx;
        lastStatsTime = now;
    }

    // ---------- player ----------
    private void play() {
        releasePlayer();
        if (stream == null) { setLive(false); return; }
        DefaultLoadControl buffering = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(75, 200, 50, 75)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new SimpleExoPlayer.Builder(this, new LowLatencyRenderersFactory(this)).setLoadControl(buffering).build();
        video.setPlayer(player);
        player.addListener(new Player.EventListener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    diagnostics.removeCallbacks(catchUp);
                    diagnostics.post(catchUp);
                }
            }
            @Override public void onPlayerError(ExoPlaybackException error) {
                setLive(false);
                noSignal.setText("NO SIGNAL · 双击重试");
            }
        });
        player.addVideoListener(new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() { setLive(true); }
        });
        player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(stream)));
        player.prepare();
        player.setPlayWhenReady(true);
        diagnostics.post(catchUp);
    }

    private void releasePlayer() {
        diagnostics.removeCallbacks(catchUp);
        appliedSpeed = 1.0f;
        lastCounters = null;
        lastRenderedFrames = 0;
        video.setPlayer(null);
        if (player != null) { player.release(); player = null; }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!demo) play();
        lastRxBytes = -1;
        diagnostics.post(statsTick);
        diagnostics.post(linkWatch);
    }

    @Override protected void onStop() {
        diagnostics.removeCallbacks(statsTick);
        diagnostics.removeCallbacks(linkWatch);
        releasePlayer();
        super.onStop();
    }

    @Override public void onBackPressed() { finishWithResult(); }

    private void finishWithResult() {
        if (isFinishing()) return;
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_LIGHT, lightOn));
        finish();
    }

    // ---------- snapshot ----------
    private void takeSnapshot() {
        View surface = video.getVideoSurfaceView();
        Bitmap bmp = surface instanceof TextureView ? ((TextureView) surface).getBitmap() : null;
        if (bmp == null || player == null) {
            Toast.makeText(this, "无视频画面，无法截图", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
            Toast.makeText(this, "需要存储权限，授权后再截图", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = GallerySaver.save(this, bmp);
        Toast.makeText(this, uri != null ? "已保存到相册 Pictures/ThingsHello" : "截图保存失败", Toast.LENGTH_SHORT).show();
    }

    // ---------- debug demo (BuildConfig.DEBUG only) ----------
    private void applyDemo() {
        video.setBackgroundColor(0xFF1A1A22);
        setLive(true);
        noSignal.setVisibility(View.GONE);
        pillStream.setText("CSI · 1280×720 · 30FPS · RTSP");
        pillBuf.setText("BUF 300ms");
        pillWifi.setText("手机 WiFi -52dBm");
        pillRate.setText("4.2Mbps");
        pillServo.setText("云台 65%");
        pillPower.setText("功率 72%");
        String s = getIntent().getStringExtra(MainActivity.EXTRA_DEBUG_STATE);
        if ("demo_hidden".equals(s)) { hudVisible = false; hud.setVisibility(View.INVISIBLE); }
    }

    private int color(int id) { return ContextCompat.getColor(this, id); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
