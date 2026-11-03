package org.mark.mobile.preview

import android.content.Context
import android.media.MediaCrypto
import android.os.Build
import android.os.Handler
import android.util.Log
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener
import java.util.ArrayList

/**
 * CSI 预览专用低延迟解码器配置；不支持低延迟特性的设备自动保持常规解码。
 */
class LowLatencyRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(object : MediaCodecVideoRenderer(
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            50
        ) {
            override fun getMediaCodecConfiguration(
                codecInfo: MediaCodecInfo,
                format: Format,
                crypto: MediaCrypto?,
                codecOperatingRate: Float
            ): MediaCodecAdapter.Configuration {
                val config = super.getMediaCodecConfiguration(codecInfo, format, crypto, codecOperatingRate)
                val supported = Build.VERSION.SDK_INT >= 30 &&
                    codecInfo.capabilities?.isFeatureSupported("low-latency") == true
                // 使用 API 30 KEY_LOW_LATENCY 字面量以保持 compileSdk 28 兼容编译
                if (supported) {
                    config.mediaFormat.setInteger("low-latency", 1)
                }
                Log.i("CsiLatency", "decoder=${codecInfo.name} lowLatency=$supported")
                return config
            }
        })
    }
}
