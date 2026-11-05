package org.mark.mobile

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import org.mark.mobile.databinding.ActivityPreviewCyberBinding
import org.mark.mobile.preview.LowLatencyRenderersFactory
import org.mark.mobile.repository.CarControlRepository
import org.mark.mobile.ui.cyber.CutShapeHelper
import org.mark.mobile.ui.cyber.ScanlineDrawable
import org.mark.mobile.usecase.MediaStoreSnapshotStore
import org.mark.mobile.usecase.SaveSnapshotUseCase
import org.mark.mobile.usecase.SnapshotResult
import org.mark.mobile.utils.WifiRssi
import org.mark.mobile.utils.colorCompat
import org.mark.mobile.utils.dp
import org.mark.mobile.utils.dpF
import org.mark.mobile.utils.hide
import org.mark.mobile.utils.show
import org.mark.mobile.viewmodel.H264UiState
import org.mark.mobile.viewmodel.H264ViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fullscreen CSI preview (2077 HUD, MVVM View Layer). ExoPlayer handles RTSP/H264 over TCP.
 */
class H264Activity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_SERVO = "servo"
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val EXTRA_LIGHT = "light"

        private const val REQ_STORAGE = 7
        private const val CATCH_UP_POLL_MS = 200L
        private const val STATS_POLL_MS = 1000L
        private const val FADE_DURATION_MS = 200L
    }

    private lateinit var binding: ActivityPreviewCyberBinding
    private lateinit var viewModel: H264ViewModel
    private lateinit var saveSnapshotUseCase: SaveSnapshotUseCase

    private val uiHandler = Handler(Looper.getMainLooper())
    private var player: SimpleExoPlayer? = null
    private var stream: Uri? = null

    private var lastStatsTime: Long = 0L
    private var lastRxBytes: Long = -1L
    private var lastRenderedFrames: Int = 0
    private var lastCounters: DecoderCounters? = null
    private var appliedSpeed: Float = 1.0f

    private var lastRenderedHudVisible: Boolean? = null
    private var lastRenderedHintVisible: Boolean? = null
    private var lastRenderedLightOn: Boolean? = null
    private var lastRenderedAutoHide: Boolean? = null

    private val catchUp = object : Runnable {
        override fun run() {
            val currentPlayer = player ?: return
            val isReady = currentPlayer.playbackState == Player.STATE_READY
            val bufferedMs = if (isReady) currentPlayer.totalBufferedDuration else 0L
            val next = viewModel.computeCatchUpSpeed(isReady, bufferedMs, appliedSpeed)
            if (next != appliedSpeed) {
                appliedSpeed = next
                currentPlayer.setPlaybackSpeed(next)
            }
            uiHandler.postDelayed(this, CATCH_UP_POLL_MS)
        }
    }

    private val statsTick = object : Runnable {
        override fun run() {
            if (!viewModel.isDemo) {
                updateHudStats()
            }
            uiHandler.postDelayed(this, STATS_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewCyberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val debugState = if (BuildConfig.DEBUG) {
            intent.getStringExtra(MainActivity.EXTRA_DEBUG_STATE)
        } else {
            null
        }
        val isDemo = debugState != null
        val initialLightOn = intent.getBooleanExtra(EXTRA_LIGHT, false)
        val servoPercent = intent.getIntExtra(EXTRA_SERVO, 50)
        val speedLimit = intent.getIntExtra(EXTRA_SPEED_LIMIT, -1)

        saveSnapshotUseCase = SaveSnapshotUseCase(MediaStoreSnapshotStore(this))
        viewModel = ViewModelProvider(
            this,
            H264ViewModel.Factory(
                controlRepository = CarControlRepository(),
                isDemo = isDemo,
                debugState = debugState,
                initialLightOn = initialLightOn,
                servoPercent = servoPercent,
                speedLimit = speedLimit
            )
        ).get(H264ViewModel::class.java)

        val host = intent.getStringExtra(EXTRA_HOST)
        if (!host.isNullOrBlank()) {
            stream = Uri.Builder()
                .scheme("rtsp")
                .encodedAuthority("${host.trim()}:8554")
                .appendPath("csi")
                .build()
        }

        applyCyberChrome()
        setupActions()
        setupGesturesAndInsets()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this, Observer { state ->
            renderUiState(state)
        })

        viewModel.finishPreview.observe(this, Observer { event ->
            val lightOn = event.getContentIfNotHandled() ?: return@Observer
            if (isFinishing) return@Observer
            setResult(RESULT_OK, Intent().putExtra(EXTRA_LIGHT, lightOn))
            finish()
        })
    }

    private fun applyCyberChrome() {
        val yellow = colorCompat(R.color.cp_yellow)
        val cyan = colorCompat(R.color.cp_cyan)
        val pillBg = colorCompat(R.color.scrim_70)

        for (pill in arrayOf(
            binding.pillStream,
            binding.pillBuf,
            binding.pillWifi,
            binding.pillRate,
            binding.pillServo,
            binding.pillSpeed
        )) {
            CutShapeHelper.applyWithLeftBar(pill, pillBg, cyan, dp(2))
        }

        binding.textHint.background = CutShapeHelper.panel(pillBg)
        binding.tagRec.background = CutShapeHelper.panel(colorCompat(R.color.cp_red))

        binding.overlayTint.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x2EFF003C, 0x00000000, 0x2E02D7F2)
        )
        binding.overlayScan.background = ScanlineDrawable(resources, 0x26000000)
        binding.overlayFrame.background = CutShapeHelper.stroked(
            0x00000000,
            (yellow and 0x00FFFFFF) or 0x99000000.toInt(),
            dpF(2),
            cutTr = dpF(30),
            cutBl = dpF(30)
        )

        binding.btnBackPv.background = CutShapeHelper.stroked(pillBg, yellow, dpF(1), cutBr = dpF(10))
        binding.btnSnapPv.background = CutShapeHelper.stroked(pillBg, yellow, dpF(1), cutBr = dpF(10))
        binding.btnSnapPv.setColorFilter(yellow)
        binding.btnAutohidePv.background = CutShapeHelper.stroked(pillBg, yellow, dpF(1), cutBr = dpF(10))
        binding.btnAutohidePv.setColorFilter(yellow)

        if (viewModel.isDemo) {
            binding.playerFull.setBackgroundColor(0xFF1A1A22.toInt())
        }
    }

    private fun setupActions() {
        binding.btnBackPv.setOnClickListener { viewModel.onBackRequested() }
        binding.btnSnapPv.setOnClickListener {
            takeSnapshot()
            viewModel.bumpHud()
        }
        binding.btnLightPv.setOnClickListener { viewModel.onToggleLight() }
        binding.btnAutohidePv.setOnClickListener { viewModel.onToggleAutoHide() }
    }

    private fun setupGesturesAndInsets() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val hasError = player?.playerError != null
                if (viewModel.onScreenDoubleTap(hasError)) {
                    play()
                }
                return true
            }
        })
        binding.previewRoot.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        binding.previewRoot.setOnApplyWindowInsetsListener { _, insets ->
            var l = 0
            var t = 0
            var r = 0
            var b = 0
            if (Build.VERSION.SDK_INT >= 28) {
                val cutout = insets.displayCutout
                if (cutout != null) {
                    l = cutout.safeInsetLeft
                    t = cutout.safeInsetTop
                    r = cutout.safeInsetRight
                    b = cutout.safeInsetBottom
                }
            }
            binding.hudRoot.setPadding(l, t, r, b)
            insets
        }
    }

    private fun renderUiState(state: H264UiState) {
        if (state.isLive && state.isHudVisible) {
            binding.tagRec.show()
            binding.textNoSignal.hide()
        } else if (state.isLive) {
            // HUD 隐藏中：REC 由 setFilterOverlaysVisible 处理，这里只保证无信号文案关掉
            binding.textNoSignal.hide()
        } else {
            binding.tagRec.hide()
            binding.textNoSignal.show()
        }
        binding.textNoSignal.text = state.noSignalText

        binding.pillStream.text = state.streamPillText
        binding.pillBuf.text = state.bufPillText
        binding.pillWifi.text = state.wifiPillText
        binding.pillRate.text = state.ratePillText
        binding.pillServo.text = state.servoPillText
        binding.pillSpeed.text = state.speedPillText

        if (lastRenderedLightOn != state.isLightOn) {
            lastRenderedLightOn = state.isLightOn
            renderLight(state.isLightOn)
        }
        if (lastRenderedAutoHide != state.autoHideEnabled) {
            lastRenderedAutoHide = state.autoHideEnabled
            renderAutoHide(state.autoHideEnabled)
        }

        if (lastRenderedHudVisible == null) {
            lastRenderedHudVisible = state.isHudVisible
            binding.hud.animate().cancel()
            binding.hud.alpha = if (state.isHudVisible) 1f else 0f
            binding.hud.visibility = if (state.isHudVisible) View.VISIBLE else View.INVISIBLE
            setFilterOverlaysVisible(state.isHudVisible, animate = false)
        } else if (lastRenderedHudVisible != state.isHudVisible) {
            lastRenderedHudVisible = state.isHudVisible
            animateHud(state.isHudVisible)
            setFilterOverlaysVisible(state.isHudVisible, animate = true)
        }

        if (lastRenderedHintVisible == null) {
            lastRenderedHintVisible = state.isHintVisible
            binding.textHint.alpha = if (state.isHintVisible) 1f else 0f
        } else if (lastRenderedHintVisible != state.isHintVisible) {
            lastRenderedHintVisible = state.isHintVisible
            binding.textHint.animate()
                .alpha(if (state.isHintVisible) 1f else 0f)
                .setDuration(FADE_DURATION_MS)
                .start()
        }
    }

    private fun animateHud(show: Boolean) {
        binding.hud.animate().cancel()
        if (show) {
            binding.hud.visibility = View.VISIBLE
        }
        binding.hud.animate()
            .alpha(if (show) 1f else 0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction {
                if (viewModel.uiState.value?.isHudVisible == false) {
                    binding.hud.visibility = View.INVISIBLE
                }
            }
            .start()
    }

    /** HUD 隐藏时同步关掉滤镜、中心焦点和 REC 红标，露出干净画面 */
    private fun setFilterOverlaysVisible(show: Boolean, animate: Boolean) {
        val live = viewModel.uiState.value?.isLive == true
        val overlays = arrayOf(binding.overlayTint, binding.overlayScan, binding.overlayFrame, binding.imgReticle)
        for (view in overlays) {
            applyOverlayVisibility(view, show, animate)
        }
        // REC 仅在有信号且 HUD 显示时出现
        applyOverlayVisibility(binding.tagRec, show && live, animate)
    }

    private fun applyOverlayVisibility(view: View, show: Boolean, animate: Boolean) {
        view.animate().cancel()
        if (!animate) {
            view.alpha = if (show) 1f else 0f
            view.visibility = if (show) View.VISIBLE else View.INVISIBLE
            return
        }
        if (show) {
            view.visibility = View.VISIBLE
        }
        view.animate()
            .alpha(if (show) 1f else 0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction {
                if (!show) {
                    view.visibility = View.INVISIBLE
                }
            }
            .start()
    }

    private fun renderAutoHide(enabled: Boolean) {
        // 与车灯按钮同一套描边：始终 cp_yellow + 1dp，靠填充区分开关态
        val yellow = colorCompat(R.color.cp_yellow)
        binding.btnAutohidePv.background = CutShapeHelper.stroked(
            if (enabled) yellow else colorCompat(R.color.scrim_70),
            yellow,
            dpF(1),
            cutBr = dpF(10)
        )
        binding.btnAutohidePv.setColorFilter(if (enabled) colorCompat(R.color.cp_bg) else yellow)
        binding.btnAutohidePv.isSelected = enabled
        binding.btnAutohidePv.contentDescription = if (enabled) "自动隐藏界面：开" else "自动隐藏界面：关"
    }

    private fun renderLight(lightOn: Boolean) {
        val yellow = colorCompat(R.color.cp_yellow)
        binding.btnLightPv.background = CutShapeHelper.stroked(
            if (lightOn) yellow else colorCompat(R.color.scrim_70),
            yellow,
            dpF(1),
            cutBr = dpF(10)
        )
        binding.btnLightPv.setColorFilter(if (lightOn) colorCompat(R.color.cp_bg) else yellow)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersive()
        }
    }

    private fun enterImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    private fun updateHudStats() {
        val rssi = WifiRssi.read(this)
        val now = SystemClock.elapsedRealtime()
        val currentPlayer = player

        var fps: String? = null
        var rateMbps: String? = null
        val rx = TrafficStats.getUidRxBytes(Process.myUid())
        if (rx != TrafficStats.UNSUPPORTED.toLong() && lastRxBytes >= 0 && now > lastStatsTime && currentPlayer != null) {
            val mbps = (rx - lastRxBytes) * 8.0 / 1e6 / ((now - lastStatsTime) / 1000.0)
            rateMbps = String.format(Locale.US, "%.1f", mbps)
        }

        if (currentPlayer != null) {
            val counters = currentPlayer.videoDecoderCounters
            if (counters != null) {
                counters.ensureUpdated()
                val frames = counters.renderedOutputBufferCount
                if (counters === lastCounters && now > lastStatsTime && frames >= lastRenderedFrames) {
                    fps = ((frames - lastRenderedFrames) * 1000.0 / (now - lastStatsTime)).roundToInt().toString()
                }
                lastRenderedFrames = frames
            }
            lastCounters = counters

            val ready = currentPlayer.playbackState == Player.STATE_READY
            val size = currentPlayer.videoSize
            viewModel.onHudStatsUpdated(
                rssi = rssi,
                hasPlayer = true,
                isReady = ready,
                bufferedMs = currentPlayer.totalBufferedDuration,
                videoWidth = size.width,
                videoHeight = size.height,
                fps = fps,
                rateMbps = rateMbps
            )
        } else {
            viewModel.onHudStatsUpdated(
                rssi = rssi,
                hasPlayer = false,
                isReady = false,
                bufferedMs = 0L,
                videoWidth = 0,
                videoHeight = 0,
                fps = null,
                rateMbps = rateMbps
            )
        }

        lastRxBytes = rx
        lastStatsTime = now
    }

    // ---------- player ----------
    private fun play() {
        releasePlayer()
        val targetUri = stream
        if (targetUri == null) {
            viewModel.onVideoLiveChanged(false)
            return
        }

        val buffering = DefaultLoadControl.Builder()
            .setBufferDurationsMs(75, 200, 50, 75)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = SimpleExoPlayer.Builder(this, LowLatencyRenderersFactory(this))
            .setLoadControl(buffering)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            uiHandler.removeCallbacks(catchUp)
                            uiHandler.post(catchUp)
                        }
                    }

                    override fun onPlayerError(error: ExoPlaybackException) {
                        viewModel.onPlayerError()
                    }

                    override fun onRenderedFirstFrame() {
                        viewModel.onVideoLiveChanged(true)
                    }
                })
                setMediaSource(
                    RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .createMediaSource(MediaItem.fromUri(targetUri))
                )
                prepare()
                playWhenReady = true
            }

        player = exoPlayer
        binding.playerFull.player = exoPlayer
        uiHandler.post(catchUp)
    }

    private fun releasePlayer() {
        uiHandler.removeCallbacks(catchUp)
        appliedSpeed = 1.0f
        lastCounters = null
        lastRenderedFrames = 0
        binding.playerFull.player = null
        player?.release()
        player = null
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.isDemo) {
            play()
        }
        lastRxBytes = -1L
        uiHandler.post(statsTick)
        viewModel.onStart()
    }

    override fun onStop() {
        uiHandler.removeCallbacks(statsTick)
        viewModel.onStop()
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.onBackRequested()
    }

    // ---------- snapshot ----------
    private fun takeSnapshot() {
        val surface = binding.playerFull.videoSurfaceView
        val frame: Bitmap? = if (player != null && surface is TextureView) surface.bitmap else null

        when (saveSnapshotUseCase(frame)) {
            SnapshotResult.NO_FRAME -> {
                Toast.makeText(this, "无视频画面，无法截图", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.NEED_STORAGE_PERMISSION -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQ_STORAGE
                )
                Toast.makeText(this, "需要存储权限，授权后再截图", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.SAVED -> {
                Toast.makeText(this, "已保存到相册 Pictures/ThingsHello", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.FAILED -> {
                Toast.makeText(this, "截图保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
