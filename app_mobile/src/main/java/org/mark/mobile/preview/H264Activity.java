package org.mark.mobile.preview;

import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import androidx.appcompat.app.AppCompatActivity;

/** Separate CSI preview. ExoPlayer handles RTSP/H264 over TCP. */
public final class H264Activity extends AppCompatActivity {
    private PlayerView video;
    private SimpleExoPlayer player;
    private TextView status;
    private TextView streamInfo;
    private long lastStatsTime;
    private int lastRenderedFrames;
    private com.google.android.exoplayer2.decoder.DecoderCounters lastCounters;
    private Uri stream;
    private final android.os.Handler diagnostics = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable bufferSample = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            updateStreamInfo();
            diagnostics.postDelayed(this, 5000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button back = new Button(this);
        back.setText("返回"); back.setOnClickListener(v -> finish()); root.addView(back);
        status = new TextView(this); root.addView(status);
        streamInfo = new TextView(this);
        streamInfo.setTextSize(12);
        int padding = (int) (8 * getResources().getDisplayMetrics().density);
        streamInfo.setPadding(padding, padding / 2, padding, padding / 2);
        root.addView(streamInfo);
        video = new PlayerView(this);
        video.setUseController(false);
        root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        Button retry = new Button(this);
        retry.setText("重试"); retry.setOnClickListener(v -> play()); root.addView(retry);
        setContentView(root);
        String host = getIntent().getStringExtra("host");
        if (host != null && !host.trim().isEmpty()) {
            stream = new Uri.Builder().scheme("rtsp").encodedAuthority(host + ":8554").appendPath("csi").build();
        }
    }

    private void play() {
        releasePlayer();
        if (stream == null) { status.setText("没有小车地址，请返回重新连接。"); return; }
        status.setText("正在连接排线摄像头…");
        streamInfo.setText("等待视频信息…");
        DefaultLoadControl buffering = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(75, 200, 50, 75)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new SimpleExoPlayer.Builder(this, new LowLatencyRenderersFactory(this)).setLoadControl(buffering).build();
        video.setPlayer(player);
        player.addListener(new Player.EventListener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) status.setText("H.264 排线摄像头");
            }
            @Override public void onPlayerError(ExoPlaybackException error) {
                status.setText("视频播放失败，点击重试：" + error.getMessage());
            }
        });
        player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(stream)));
        player.prepare();
        player.setPlayWhenReady(true);
        diagnostics.post(bufferSample);
    }
    private void updateStreamInfo() {
        com.google.android.exoplayer2.video.VideoSize size = player.getVideoSize();
        String dimensions = size.width > 0 && size.height > 0
                ? size.width + " × " + size.height : "分辨率 —";
        long now = android.os.SystemClock.elapsedRealtime();
        com.google.android.exoplayer2.decoder.DecoderCounters counters = player.getVideoDecoderCounters();
        String fps = "—";
        String dropped = "—";
        if (counters != null) {
            counters.ensureUpdated();
            int frames = counters.renderedOutputBufferCount;
            if (counters == lastCounters && now > lastStatsTime && frames >= lastRenderedFrames) {
                fps = String.format(java.util.Locale.US, "%.1f",
                        (frames - lastRenderedFrames) * 1000.0 / (now - lastStatsTime));
            }
            dropped = Integer.toString(counters.droppedBufferCount);
            lastRenderedFrames = frames;
        }
        lastCounters = counters;
        lastStatsTime = now;
        String text = dimensions + "  ·  H.264 / RTSP-TCP\n渲染 " + fps
                + " 帧/秒（近5秒）  ·  累计丢帧 " + dropped
                + "  ·  播放缓冲 " + player.getTotalBufferedDuration() + " ms（非总延迟）";
        if (!text.contentEquals(streamInfo.getText())) streamInfo.setText(text);
    }

    @Override protected void onStart() { super.onStart(); play(); }
    private void releasePlayer() {
        diagnostics.removeCallbacks(bufferSample);
        lastCounters = null;
        lastStatsTime = 0;
        lastRenderedFrames = 0;
        video.setPlayer(null);
        if (player != null) { player.release(); player = null; }
    }
    @Override protected void onStop() { releasePlayer(); super.onStop(); }
}
