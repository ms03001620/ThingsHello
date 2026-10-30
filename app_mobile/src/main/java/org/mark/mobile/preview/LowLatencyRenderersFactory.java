package org.mark.mobile.preview;

import android.content.Context;
import android.media.MediaCrypto;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.ArrayList;

/** Decoder tuning scoped to CSI preview. Unsupported devices retain normal decoding. */
final class LowLatencyRenderersFactory extends DefaultRenderersFactory {
    LowLatencyRenderersFactory(Context context) { super(context); }

    @Override protected void buildVideoRenderers(Context context, int extensionMode,
            MediaCodecSelector selector, boolean fallback, Handler handler,
            VideoRendererEventListener listener, long joiningTimeMs, ArrayList<Renderer> out) {
        out.add(new MediaCodecVideoRenderer(context, selector, joiningTimeMs, fallback,
                handler, listener, 50) {
            @Override protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(
                    MediaCodecInfo info, Format format, MediaCrypto crypto, float operatingRate) {
                MediaCodecAdapter.Configuration configuration =
                        super.getMediaCodecConfiguration(info, format, crypto, operatingRate);
                boolean supported = Build.VERSION.SDK_INT >= 30 && info.capabilities != null
                        && info.capabilities.isFeatureSupported("low-latency");
                // API 30's KEY_LOW_LATENCY literal keeps the existing compileSdk 28 build compatible.
                if (supported) configuration.mediaFormat.setInteger("low-latency", 1);
                Log.i("CsiLatency", "decoder=" + info.name + " lowLatency=" + supported);
                return configuration;
            }
        });
    }
}
