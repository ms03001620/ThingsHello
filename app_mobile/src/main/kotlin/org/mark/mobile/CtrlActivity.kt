package org.mark.mobile

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import org.mark.mobile.databinding.ActivityCtrlBinding
import org.mark.mobile.databinding.ItemCyberActionBinding
import org.mark.mobile.preview.LowLatencyRenderersFactory
import org.mark.mobile.repository.CarControlRepository
import org.mark.mobile.repository.CarPrefsRepository
import org.mark.mobile.ui.cyber.CutShapeHelper
import org.mark.mobile.ui.cyber.ParallelogramDrawable
import org.mark.mobile.ui.cyber.ScanlineDrawable
import org.mark.mobile.usecase.MediaStoreSnapshotStore
import org.mark.mobile.usecase.SaveSnapshotUseCase
import org.mark.mobile.usecase.SnapshotResult
import org.mark.mobile.utils.PreferUtils
import org.mark.mobile.utils.WifiRssi
import org.mark.mobile.utils.colorCompat
import org.mark.mobile.utils.dp
import org.mark.mobile.utils.dpF
import org.mark.mobile.utils.hide
import org.mark.mobile.utils.show
import org.mark.mobile.utils.sp
import org.mark.mobile.viewmodel.CtrlUiState
import org.mark.mobile.viewmodel.CtrlViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cyberpunk 2077 control page (spec §3, MVVM View Layer).
 */
class CtrlActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        private const val REQ_STORAGE = 11
        private const val REQ_PREVIEW = 21
        private const val RSSI_POLL_MS = 2000L
        private const val STATS_POLL_MS = 1000L
    }

    private lateinit var binding: ActivityCtrlBinding
    private lateinit var viewModel: CtrlViewModel
    private lateinit var saveSnapshotUseCase: SaveSnapshotUseCase

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var wifiBg: ParallelogramDrawable
    private lateinit var cellBuffer: TextView
    private lateinit var cellSpeed: TextView
    private lateinit var cellFps: TextView
    private lateinit var cellBitrate: TextView

    private var player: SimpleExoPlayer? = null
    private var lastCounters: DecoderCounters? = null
    private var lastFrames: Int = 0
    private var lastStatsTime: Long = 0L
    private var lastRxBytes: Long = -1L

    private var lastRenderedLightOn: Boolean? = null
    private var lastRenderedHornPressed: Boolean? = null

    private val rssiTick = object : Runnable {
        override fun run() {
            if (!viewModel.isDemo) {
                viewModel.onWifiRssiUpdated(WifiRssi.read(this@CtrlActivity))
            }
            uiHandler.postDelayed(this, RSSI_POLL_MS)
        }
    }

    private val statsTick = object : Runnable {
        override fun run() {
            if (!viewModel.isDemo) {
                updateVideoStats()
            }
            uiHandler.postDelayed(this, STATS_POLL_MS)
        }
    }

    private val rotateTouchListener = View.OnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                viewModel.onRotatePressed(isLeft = v === binding.btnLeft)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel.onRotateReleased(isLeft = v === binding.btnLeft)
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityCtrlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isDemo = BuildConfig.DEBUG && intent.getStringExtra(MainActivity.EXTRA_DEBUG_STATE) == "demo"
        val controlRepository = CarControlRepository()
        val prefsRepository = CarPrefsRepository(PreferUtils.getInstance(this))
        saveSnapshotUseCase = SaveSnapshotUseCase(MediaStoreSnapshotStore(this))
        viewModel = ViewModelProvider(this, CtrlViewModel.Factory(controlRepository, prefsRepository, isDemo))
            .get(CtrlViewModel::class.java)

        binding.scanlineOverlay.background = ScanlineDrawable(resources, colorCompat(R.color.scanline))

        setupTopBar()
        setupVideo()
        setupTelemetry()
        setupGimbal()
        setupDrive()
        setupSpeed()
        setupActions()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this, Observer { state ->
            renderUiState(state)
        })

        viewModel.returnToConnectRefused.observe(this, Observer { event ->
            event.getContentIfNotHandled() ?: return@Observer
            if (isFinishing) return@Observer
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_REFUSED, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        })
    }

    private fun renderUiState(state: CtrlUiState) {
        binding.textDriveSub.text = state.subtitleText
        binding.textDriveSub.setTextColor(colorCompat(if (state.isConnected) R.color.cp_cyan else R.color.cp_red))

        binding.textWifi.text = if (state.wifiRssi == null) "WiFi --" else "WiFi ${state.wifiRssi}dBm"
        wifiBg.setColor(colorCompat(if (state.isWifiWeak) R.color.cp_red else R.color.cp_cyan))
        binding.textWifi.setTextColor(colorCompat(if (state.isWifiWeak) R.color.cp_white else R.color.cp_on_yellow))

        if (state.showNoSignal) binding.textVideoState.show() else binding.textVideoState.hide()
        if (state.showLiveTag) binding.tagLive.show() else binding.tagLive.hide()

        setCell(cellBuffer, state.bufferMsText, "ms")
        setCell(cellSpeed, state.speedLimit.toString(), "%")
        setCell(cellFps, state.fpsText, "FPS")
        setCell(cellBitrate, state.bitrateText, "Mbps")

        binding.sliderServo.setValue(state.servoPercent, false)
        binding.textServo.text = "${state.servoPercent}%"

        binding.sliderSpeed.setValue(state.speedLimit, false)
        binding.textSpeed.text = "${state.speedLimit}%"

        if (lastRenderedLightOn != state.isLightOn) {
            lastRenderedLightOn = state.isLightOn
            renderLight(state.isLightOn)
        }
        if (lastRenderedHornPressed != state.isHornPressed) {
            lastRenderedHornPressed = state.isHornPressed
            setActionColors(
                binding.btnHorn,
                colorCompat(if (state.isHornPressed) R.color.cp_white else R.color.cp_text_dim),
                false
            )
        }
    }

    // ---------- top bar ----------
    private fun setupTopBar() {
        val yellow = colorCompat(R.color.cp_yellow)
        binding.btnBack.background = CutShapeHelper.states(
            CutShapeHelper.stroked(0, yellow, dpF(1), cutBr = dpF(11)),
            CutShapeHelper.stroked(0x26FCEE0A, yellow, dpF(1), cutBr = dpF(11))
        )
        binding.btnBack.setOnClickListener {
            viewModel.onActivityFinishing()
            finish()
        }

        wifiBg = ParallelogramDrawable(colorCompat(R.color.cp_cyan), dpF(8))
        binding.textWifi.background = wifiBg
    }

    // ---------- video ----------
    private fun setupVideo() {
        binding.videoBox.background = CutShapeHelper.panel(colorCompat(R.color.cp_panel), cutTr = dpF(24))
        binding.videoTint.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x40FF003C, 0x00000000, 0x4002D7F2)
        )
        binding.videoScan.background = ScanlineDrawable(resources, 0x40000000)
        binding.videoCorner.background = CutShapeHelper.cornerMask(colorCompat(R.color.cp_bg), dpF(24))
        binding.btnFullscreen.background = CutShapeHelper.states(
            CutShapeHelper.panel(colorCompat(R.color.cp_yellow), cutBr = dpF(11)),
            CutShapeHelper.panel(colorCompat(R.color.cp_yellow_pressed), cutBr = dpF(11))
        )
        binding.btnFullscreen.setOnClickListener { openPreview() }
        binding.playerSmall.setOnClickListener { openPreview() }
    }

    private fun openPreview() {
        val state = viewModel.uiState.value ?: CtrlUiState()
        val intent = Intent(this, H264Activity::class.java).apply {
            putExtra(H264Activity.EXTRA_HOST, viewModel.getConnectedHost())
            putExtra(H264Activity.EXTRA_SERVO, state.servoPercent)
            putExtra(H264Activity.EXTRA_SPEED_LIMIT, state.speedLimit)
            putExtra(H264Activity.EXTRA_LIGHT, state.isLightOn)
            if (viewModel.isDemo) {
                putExtra(MainActivity.EXTRA_DEBUG_STATE, "demo")
            }
        }
        startActivityForResult(intent, REQ_PREVIEW)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PREVIEW && data != null) {
            val currentLight = viewModel.uiState.value?.isLightOn ?: false
            val lightOn = data.getBooleanExtra(H264Activity.EXTRA_LIGHT, currentLight)
            viewModel.syncLightFromPreview(lightOn)
        }
    }

    private fun startPlayer() {
        val host = viewModel.getConnectedHost()
        if (host.isNullOrBlank() || viewModel.isDemo) return

        val uri = Uri.Builder()
            .scheme("rtsp")
            .encodedAuthority("${host.trim()}:8554")
            .appendPath("csi")
            .build()
        val buffering = DefaultLoadControl.Builder()
            .setBufferDurationsMs(75, 200, 50, 75)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = SimpleExoPlayer.Builder(this, LowLatencyRenderersFactory(this))
            .setLoadControl(buffering)
            .build()
            .apply {
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        val ready = state == Player.STATE_READY
                        viewModel.onVideoLiveChanged(ready)
                        if (ready) {
                            updateVideoStats()
                        }
                    }
                })
                setMediaSource(
                    RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .createMediaSource(MediaItem.fromUri(uri))
                )
                prepare()
                playWhenReady = true
            }
        player = exoPlayer
        binding.playerSmall.player = exoPlayer
    }

    private fun releasePlayer() {
        binding.playerSmall.player = null
        player?.release()
        player = null
        lastCounters = null
        lastRxBytes = -1L
        viewModel.onVideoLiveChanged(false)
    }

    // ---------- telemetry ----------
    private fun setupTelemetry() {
        cellBuffer = addCell(binding.telemetryRow, "播放缓冲", true)
        cellSpeed = addCell(binding.telemetryRow, "限速", true)
        cellFps = addCell(binding.telemetryRow, "视频帧率", true)
        cellBitrate = addCell(binding.telemetryRow, "码率", false)
    }

    private fun addCell(row: LinearLayout, label: String, gap: Boolean): TextView {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(6))
            background = CutShapeHelper.topBarPanel(
                colorCompat(R.color.cp_panel), colorCompat(R.color.cp_cyan), dpF(2)
            )
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(colorCompat(R.color.cp_cyan))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }
        val valueView = TextView(this).apply {
            typeface = ResourcesCompat.getFont(this@CtrlActivity, R.font.orbitron_bold)
            setTextColor(colorCompat(R.color.cp_yellow))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine(true)
            text = "--"
        }
        cell.addView(labelView)
        cell.addView(valueView)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            if (gap) marginEnd = dp(6)
        }
        row.addView(cell, lp)
        return valueView
    }

    private fun valueUnit(value: String, unit: String?, unitColor: Int, unitPx: Int): CharSequence {
        val sb = SpannableStringBuilder(value)
        if (unit != null) {
            val start = sb.length
            sb.append(" ").append(unit)
            sb.setSpan(AbsoluteSizeSpan(unitPx), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(unitColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun setCell(cell: TextView, value: String?, unit: String?) {
        cell.text = if (value == null) "--" else valueUnit(value, unit, 0xB3FCEE0A.toInt(), sp(10))
    }

    private fun updateVideoStats() {
        val currentPlayer = player
        val playing = currentPlayer != null && currentPlayer.playbackState == Player.STATE_READY
        val now = SystemClock.elapsedRealtime()
        if (!playing) {
            viewModel.onVideoStatsUpdated(bufferMs = null, fps = null, bitrateMbps = null)
            lastCounters = null
            lastRxBytes = -1L
            lastStatsTime = now
            return
        }

        val bufferMs = currentPlayer!!.totalBufferedDuration.coerceAtLeast(0L).toString()

        val counters = currentPlayer.videoDecoderCounters
        var fps: String? = null
        if (counters != null) {
            counters.ensureUpdated()
            if (counters === lastCounters && now > lastStatsTime) {
                val delta = (counters.renderedOutputBufferCount - lastFrames) * 1000f / (now - lastStatsTime)
                fps = delta.roundToInt().toString()
            }
            lastFrames = counters.renderedOutputBufferCount
        }
        lastCounters = counters

        val rx = TrafficStats.getUidRxBytes(Process.myUid())
        var mbps: String? = null
        if (rx != TrafficStats.UNSUPPORTED.toLong() && lastRxBytes >= 0 && now > lastStatsTime) {
            val bits = (rx - lastRxBytes) * 8.0 * 1000.0 / (now - lastStatsTime)
            mbps = String.format(Locale.US, "%.1f", bits / 1_000_000.0)
        }
        lastRxBytes = rx
        lastStatsTime = now
        viewModel.onVideoStatsUpdated(bufferMs = bufferMs, fps = fps, bitrateMbps = mbps)
    }

    // ---------- gimbal ----------
    private fun setupGimbal() {
        val cyan = colorCompat(R.color.cp_cyan)
        binding.cardGimbal.background = CutShapeHelper.panel(colorCompat(R.color.cp_panel), cutBr = dpF(14))
        binding.sliderServo.setListener { value, fromUser ->
            if (fromUser) viewModel.onServoChanged(value)
        }
        binding.btnRecenter.background = CutShapeHelper.states(
            CutShapeHelper.stroked(0, cyan, dpF(1), cutBr = dpF(9)),
            CutShapeHelper.stroked(0x2602D7F2, cyan, dpF(1), cutBr = dpF(9))
        )
        binding.btnRecenter.setOnClickListener { viewModel.onRecenterServo() }
    }

    // ---------- drive ----------
    private fun setupDrive() {
        binding.joystick.setListener { angle, power ->
            viewModel.onJoystickMove(angle, power)
        }

        val cyan = colorCompat(R.color.cp_cyan)
        for (btn in arrayOf(binding.btnLeft, binding.btnRight)) {
            btn.background = CutShapeHelper.states(
                CutShapeHelper.stroked(colorCompat(R.color.cyan_08), cyan, dpF(2), cutBr = dpF(16)),
                CutShapeHelper.stroked(cyan, cyan, dpF(2), cutBr = dpF(16))
            )
            btn.imageTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(colorCompat(R.color.cp_on_yellow), cyan)
            )
            btn.isClickable = true
            btn.setOnTouchListener(rotateTouchListener)
        }
    }

    private fun setupSpeed() {
        binding.sliderSpeed.setListener { value, fromUser ->
            if (fromUser) viewModel.onSpeedChanged(value)
        }
    }

    // ---------- actions ----------
    private fun setupActions() {
        bindAction(binding.btnLight, R.drawable.ic_light, "车灯 关")
        binding.btnLight.root.setOnClickListener { viewModel.onToggleLight() }

        bindAction(binding.btnHorn, R.drawable.ic_buzzer, "鸣笛 按住")
        binding.btnHorn.root.background = CutShapeHelper.states(
            actionBg(colorCompat(R.color.cp_panel)),
            actionBg(colorCompat(R.color.cp_red))
        )
        binding.btnHorn.root.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    viewModel.onHornDown()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel.onHornUp()
            }
            false
        }

        bindAction(binding.btnSnapshot, R.drawable.ic_snapshot, "截图")
        binding.btnSnapshot.root.background = CutShapeHelper.states(
            actionBg(colorCompat(R.color.cp_panel)),
            actionBg(0xFF22222E.toInt())
        )
        binding.btnSnapshot.root.setOnClickListener { takeSnapshot() }
    }

    private fun actionBg(fill: Int): Drawable = CutShapeHelper.bottomEdgeCut(
        fill, colorCompat(R.color.cp_action_edge), dpF(2), dpF(15), dpF(14)
    )

    private fun bindAction(item: ItemCyberActionBinding, icon: Int, text: String) {
        item.actionIcon.setImageResource(icon)
        item.actionText.text = text
        setActionColors(item, colorCompat(R.color.cp_text_dim), false)
    }

    private fun setActionColors(item: ItemCyberActionBinding, c: Int, bold: Boolean) {
        item.actionIcon.imageTintList = ColorStateList.valueOf(c)
        item.actionText.setTextColor(c)
        item.actionText.setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderLight(lightOn: Boolean) {
        binding.btnLight.actionText.text = if (lightOn) "车灯 开" else "车灯 关"
        binding.btnLight.root.background = actionBg(colorCompat(if (lightOn) R.color.cp_yellow else R.color.cp_panel))
        setActionColors(
            binding.btnLight,
            colorCompat(if (lightOn) R.color.cp_on_yellow else R.color.cp_text_dim),
            lightOn
        )
    }

    // ---------- screenshot ----------
    private fun takeSnapshot() {
        val surface = binding.playerSmall.videoSurfaceView
        val currentPlayer = player
        val frame: Bitmap? = if (
            currentPlayer != null &&
            currentPlayer.playbackState == Player.STATE_READY &&
            surface is TextureView
        ) {
            surface.bitmap
        } else {
            null
        }

        when (saveSnapshotUseCase(frame)) {
            SnapshotResult.NO_FRAME -> {
                Toast.makeText(this, "暂无视频画面，无法截图", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.NEED_STORAGE_PERMISSION -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQ_STORAGE
                )
                Toast.makeText(this, "需要存储权限，授权后请再点一次截图", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.SAVED -> {
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
            SnapshotResult.FAILED -> {
                Toast.makeText(this, "截图保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- lifecycle ----------
    override fun onStart() {
        super.onStart()
        startPlayer()
        uiHandler.post(rssiTick)
        uiHandler.post(statsTick)
    }

    override fun onResume() {
        super.onResume()
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        viewModel.onResume(host, port)
    }

    override fun onPause() {
        viewModel.onPause()
        super.onPause()
    }

    override fun onStop() {
        uiHandler.removeCallbacks(rssiTick)
        uiHandler.removeCallbacks(statsTick)
        viewModel.onStop()
        releasePlayer()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.onActivityFinishing()
        super.onBackPressed()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            viewModel.onActivityFinishing()
        }
        super.onDestroy()
    }
}
