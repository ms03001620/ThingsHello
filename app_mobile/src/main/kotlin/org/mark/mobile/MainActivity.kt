package org.mark.mobile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.mark.mobile.databinding.ActivityMainBinding
import org.mark.mobile.repository.MainRepository
import org.mark.mobile.ui.cyber.CutShapeHelper
import org.mark.mobile.ui.cyber.ScanlineDrawable
import org.mark.mobile.utils.PreferUtils
import org.mark.mobile.utils.hide
import org.mark.mobile.utils.show
import org.mark.mobile.viewmodel.ConnectStatus
import org.mark.mobile.viewmodel.MainUiState
import org.mark.mobile.viewmodel.MainViewModel
import kotlin.math.roundToInt

/**
 * Cyberpunk 2077 Connect screen (MVVM View Layer).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REFUSED = "extra_refused"
        /** Debug only: force UI state for screenshots (idle/connecting/online/refused). */
        const val EXTRA_DEBUG_STATE = "debug_state"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private var beamAnimator: ObjectAnimator? = null
    private var barWidthPx: Int = 0
    private var cutM: Float = 0f
    private var cutL: Float = 0f
    private var cutInputBr: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        window.statusBarColor = ContextCompat.getColor(this, R.color.cp_bg)
        setContentView(binding.root)

        val repository = MainRepository(PreferUtils.getInstance(this))
        viewModel = ViewModelProvider(this, MainViewModel.Factory(repository))
            .get(MainViewModel::class.java)

        barWidthPx = dp(3)
        cutM = dp(14).toFloat()
        cutL = dp(22).toFloat()
        cutInputBr = dp(14).toFloat()

        binding.scanlineOverlay.background = ScanlineDrawable(
            resources, ContextCompat.getColor(this, R.color.scanline)
        )
        binding.scanlineOverlay.alpha = 1f

        applyCyberChrome()
        setupInputs()
        setupActions()
        observeViewModel()

        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(EXTRA_REFUSED, false)) {
            intent.removeExtra(EXTRA_REFUSED)
            viewModel.onConnectionRefusedExtra()
            return
        }

        if (BuildConfig.DEBUG) {
            val s = intent.getStringExtra(EXTRA_DEBUG_STATE)
            if (s == "preview_demo" || s == "preview_demo_hidden") {
                val pv = Intent(this, H264Activity::class.java).apply {
                    putExtra(EXTRA_DEBUG_STATE, s.substring("preview_".length))
                }
                startActivity(pv)
                return
            }
            if (s == "ctrl" || s == "ctrl_demo") {
                val ctrl = Intent(this, CtrlActivity::class.java).apply {
                    putExtra(EXTRA_DEBUG_STATE, if (s == "ctrl_demo") "demo" else "none")
                }
                startActivity(ctrl)
                return
            }
            if (s != null) {
                val portText = binding.editPort.text?.toString().orEmpty()
                viewModel.forceDebugState(s, portText)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.initialInput.observe(this, Observer { (initialHost, initialPort) ->
            if (!initialHost.isNullOrEmpty() && binding.editHost.text.isNullOrEmpty()) {
                binding.editHost.setText(initialHost)
            }
            if (!initialPort.isNullOrEmpty()) {
                binding.editPort.setText(initialPort)
            }
        })

        viewModel.recentAddresses.observe(this, Observer { addresses ->
            renderRecentAddresses(addresses)
        })

        viewModel.uiState.observe(this, Observer { state ->
            renderUiState(state)
        })

        viewModel.navigateToCtrl.observe(this, Observer { event ->
            val target = event.getContentIfNotHandled() ?: return@Observer
            openControl(target.first, target.second)
        })
    }

    private fun applyCyberChrome() {
        val panel = ContextCompat.getColor(this, R.color.cp_panel)
        val yellow = ContextCompat.getColor(this, R.color.cp_yellow)
        val cyan = ContextCompat.getColor(this, R.color.cp_cyan)
        val red = ContextCompat.getColor(this, R.color.cp_red)
        val cyan07 = ContextCompat.getColor(this, R.color.cyan_07)
        val cyan12 = ContextCompat.getColor(this, R.color.cyan_12)
        val red13 = ContextCompat.getColor(this, R.color.red_13)

        CutShapeHelper.apply(binding.cardCar, cyan07, 0f, dp(28).toFloat(), 0f, dp(27).toFloat())

        CutShapeHelper.applyWithLeftBar(binding.wrapIp, panel, yellow, barWidthPx, 0f, 0f, cutInputBr, 0f)
        CutShapeHelper.applyWithLeftBar(binding.wrapPort, panel, yellow, barWidthPx, 0f, 0f, cutInputBr, 0f)

        CutShapeHelper.applyWithLeftBar(binding.cardStatusOnline, cyan12, cyan, barWidthPx, 0f, 0f, 0f, cutM)
        CutShapeHelper.applyWithLeftBar(binding.cardStatusRefused, red13, red, barWidthPx, 0f, 0f, 0f, cutM)

        CutShapeHelper.applyButton(
            binding.btnConnect, yellow,
            ContextCompat.getColor(this, R.color.yellow_30), 0f, cutL, 0f, cutL
        )

        CutShapeHelper.applyButton(
            binding.btnRetry, red,
            Color.WHITE, 0f, 0f, dp(11).toFloat(), 0f
        )

        binding.progressConnect.indeterminateDrawable?.setColorFilter(
            ContextCompat.getColor(this, R.color.cp_on_yellow), PorterDuff.Mode.SRC_IN
        )

        startBeamAnimation()
    }

    private fun startBeamAnimation() {
        binding.scanBeam.post(object : Runnable {
            override fun run() {
                val parent = binding.scanBeam.parent as? View
                if (parent == null || parent.height == 0) {
                    binding.scanBeam.post(this)
                    return
                }
                val travel = parent.height * 0.5f
                beamAnimator = ObjectAnimator.ofFloat(
                    binding.scanBeam, View.TRANSLATION_Y, -travel * 0.3f, travel * 0.3f
                ).apply {
                    duration = 2000
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        })
    }

    private fun setupInputs() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val wrap = if (v === binding.editHost) binding.wrapIp else binding.wrapPort
            val yellow = ContextCompat.getColor(this, R.color.cp_yellow)
            val panel = ContextCompat.getColor(this, R.color.cp_panel)
            val width = if (hasFocus) dp(4) else barWidthPx
            CutShapeHelper.applyWithLeftBar(wrap, panel, yellow, width, 0f, 0f, cutInputBr, 0f)
        }
        binding.editHost.onFocusChangeListener = focusListener
        binding.editPort.onFocusChangeListener = focusListener

        binding.editHost.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.textIpError.hide()
                viewModel.onHostTextChanged()
            }
        })
    }

    private fun setupActions() {
        binding.btnConnect.setOnClickListener {
            val host = binding.editHost.text?.toString().orEmpty()
            val port = binding.editPort.text?.toString().orEmpty()
            val attempted = viewModel.onConnectClicked(host, port)
            if (attempted) {
                hideKeyboard()
            }
        }

        binding.btnConnect.setOnTouchListener { _, event ->
            if (viewModel.uiState.value?.status == ConnectStatus.CONNECTING) {
                return@setOnTouchListener false
            }
            val yellow = ContextCompat.getColor(this, R.color.cp_yellow)
            val pressed = ContextCompat.getColor(this, R.color.cp_yellow_pressed)
            val strokeColor = ContextCompat.getColor(this, R.color.yellow_30)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    CutShapeHelper.applyButton(binding.btnConnect, pressed, strokeColor, 0f, cutL, 0f, cutL)
                    binding.btnConnect.translationY = dp(1).toFloat()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    CutShapeHelper.applyButton(binding.btnConnect, yellow, strokeColor, 0f, cutL, 0f, cutL)
                    binding.btnConnect.translationY = 0f
                }
            }
            false
        }

        binding.btnRetry.setOnClickListener {
            hideKeyboard()
            val host = binding.editHost.text?.toString().orEmpty()
            val port = binding.editPort.text?.toString().orEmpty()
            viewModel.attemptConnect(host, port)
        }
    }

    private fun renderRecentAddresses(addresses: List<String>) {
        binding.listRecent.removeAllViews()
        if (addresses.isEmpty()) {
            val empty = TextView(this).apply {
                text = "—"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cp_text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                alpha = 0.5f
            }
            binding.listRecent.addView(empty)
            return
        }

        for (host in addresses) {
            val row = TextView(this).apply {
                text = host
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cp_text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(3), 0, dp(3))
                setOnClickListener {
                    binding.editHost.setText(host)
                    binding.editHost.setSelection(host.length)
                }
            }
            binding.listRecent.addView(row)
        }
    }

    private fun renderUiState(state: MainUiState) {
        if (state.hasInputError) {
            binding.textIpError.setText(R.string.cp_input_error)
            binding.textIpError.show()
            if (state.highlightHostError) {
                val red = ContextCompat.getColor(this, R.color.cp_red)
                val panel = ContextCompat.getColor(this, R.color.cp_panel)
                CutShapeHelper.applyWithLeftBar(binding.wrapIp, panel, red, barWidthPx, 0f, 0f, cutInputBr, 0f)
            }
        } else {
            binding.textIpError.hide()
        }

        binding.cardStatusOnline.hide()
        binding.cardStatusRefused.hide()
        binding.progressConnect.hide()
        binding.btnConnect.isEnabled = true
        binding.btnConnect.alpha = 1f
        binding.textConnect.alpha = 1f

        val yellow = ContextCompat.getColor(this, R.color.cp_yellow)

        when (state.status) {
            ConnectStatus.IDLE -> {
                binding.textConnect.setText(R.string.cp_connect)
                CutShapeHelper.applyButton(
                    binding.btnConnect, yellow,
                    ContextCompat.getColor(this, R.color.yellow_30), 0f, cutL, 0f, cutL
                )
            }
            ConnectStatus.CONNECTING -> {
                binding.textConnect.setText(R.string.cp_connecting)
                binding.progressConnect.show()
                binding.textStatusOnlineTitle.setText(R.string.cp_status_connecting_title)
                binding.textStatusOnlineDesc.setText(R.string.cp_status_connecting_desc)
                binding.cardStatusOnline.show()
                binding.btnConnect.isEnabled = false
                binding.btnConnect.alpha = 0.85f
            }
            ConnectStatus.ONLINE -> {
                binding.textConnect.setText(R.string.cp_connect)
                binding.textStatusOnlineTitle.setText(R.string.cp_status_online_title)
                binding.textStatusOnlineDesc.text = getString(R.string.cp_status_online_desc, state.connectedPort)
                binding.cardStatusOnline.show()
            }
            ConnectStatus.REFUSED -> {
                binding.textConnect.setText(R.string.cp_connect)
                if (state.isTimeoutError) {
                    binding.textStatusRefusedDesc.setText(R.string.cp_status_timeout_desc)
                } else {
                    binding.textStatusRefusedDesc.setText(R.string.cp_status_refused_desc)
                }
                binding.cardStatusRefused.show()
            }
        }
    }

    private fun openControl(host: String, port: Int) {
        val intent = Intent(this, CtrlActivity::class.java).apply {
            putExtra(CtrlActivity.EXTRA_HOST, host)
            putExtra(CtrlActivity.EXTRA_PORT, port)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRecentAddresses()
    }

    private fun hideKeyboard() {
        val focus = currentFocus ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focus.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).roundToInt()
    }

    override fun onDestroy() {
        beamAnimator?.cancel()
        if (isFinishing) {
            viewModel.onActivityFinishing()
        }
        super.onDestroy()
    }
}
