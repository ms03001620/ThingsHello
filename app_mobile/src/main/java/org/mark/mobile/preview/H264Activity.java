package org.mark.mobile.preview;

import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

/** Separate CSI preview. Android MediaPlayer handles RTSP/H264; no USB decoder changes. */
public final class H264Activity extends AppCompatActivity {
    private VideoView video;
    private TextView status;
    private Uri stream;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button back = new Button(this);
        back.setText("返回"); back.setOnClickListener(v -> finish()); root.addView(back);
        status = new TextView(this); root.addView(status);
        video = new VideoView(this);
        root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        Button retry = new Button(this);
        retry.setText("重试"); retry.setOnClickListener(v -> play()); root.addView(retry);
        setContentView(root);
        String host = getIntent().getStringExtra("host");
        if (host != null && !host.trim().isEmpty()) {
            stream = new Uri.Builder().scheme("rtsp").encodedAuthority(host + ":8554").appendPath("csi").build();
        }
        video.setOnPreparedListener(player -> { status.setText("H.264 排线摄像头"); video.start(); });
        video.setOnErrorListener((player, what, extra) -> {
            status.setText("视频连接失败，请确认小车 RTSP 服务已启动，点击重试。(" + what + "/" + extra + ")");
            return true;
        });
    }
    private void play() {
        video.stopPlayback();
        if (stream == null) { status.setText("没有小车地址，请返回重新连接。"); return; }
        status.setText("正在连接排线摄像头…");
        video.setVideoURI(stream);
        video.start();
    }
    @Override protected void onStart() { super.onStart(); play(); }
    @Override protected void onStop() { video.stopPlayback(); super.onStop(); }
}
