package org.mark.mobile

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieDrawable
import org.mark.mobile.databinding.ActivityMainBinding
import org.mark.mobile.repository.CarControlRepository
import org.mark.mobile.repository.CarPrefsRepository
import org.mark.mobile.ui.cyber.CutShapeHelper
import org.mark.mobile.ui.cyber.ScanlineDrawable
import org.mark.mobile.utils.PreferUtils
import org.mark.mobile.utils.colorCompat
import org.mark.mobile.utils.dp
import org.mark.mobile.utils.dpF
import org.mark.mobile.utils.hide
import org.mark.mobile.utils.show
import org.mark.mobile.viewmodel.ConnectStatus
import org.mark.mobile.viewmodel.MainUiState
import org.mark.mobile.viewmodel.MainViewModel

/**
 * Cyberpunk 2077 Connect screen (MVVM View Layer).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REFUSED = "extra_refused"
        /** Debug only: force UI state for screenshots (idle/connecting/online/refused). */
        const val EXTRA_DEBUG_STATE = "debug_state"

        private val LOTTIE_TARGETS = intArrayOf(
            R.raw.lottie_breach, R.raw.lottie_reticle, R.raw.lottie_terminal
        )

        /** Picked once per process (cold start); stays fixed for the whole session. */
        private val sessionLottieRes: Int by lazy { LOTTIE_TARGETS.random() }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private var initialInputApplied: Boolean = false
    private var barWidthPx: Int = 0
    private var cutM: Float = 0f
    private var cutL: Float = 0f
    private var cutInputBr: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialInputApplied = savedInstanceState != null
        binding = ActivityMainBinding.inflate(layoutInflater)
        window.statusBarColor = colorCompat(R.color.cp_bg)
        setContentView(binding.root)

        val controlRepository = CarControlRepository()
        val prefsRepository = CarPrefsRepository(PreferUtils.getInstance(this))
        viewModel = ViewModelProvider(this, MainViewModel.Factory(controlRepository, prefsRepository))
            .get(MainViewModel::class.java)

        barWidthPx = dp(3)
        cutM = dpF(14)
        cutL = dpF(22)
        cutInputBr = dpF(14)

        binding.scanlineOverlay.background = ScanlineDrawable(resources, colorCompat(R.color.scanline))
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
            if (!initialInputApplied) {
                initialInputApplied = true
                if (!initialHost.isNullOrEmpty()) {
                    binding.editHost.setText(initialHost)
                    binding.editHost.setSelection(initialHost.length)
                }
                if (!initialPort.isNullOrEmpty()) {
                    binding.editPort.setText(initialPort)
                }
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
        val panel = colorCompat(R.color.cp_panel)
        val yellow = colorCompat(R.color.cp_yellow)
        val cyan = colorCompat(R.color.cp_cyan)
        val red = colorCompat(R.color.cp_red)

        CutShapeHelper.apply(binding.cardCar, colorCompat(R.color.cyan_07), cutTr = dpF(28), cutBl = dpF(27))

        CutShapeHelper.applyWithLeftBar(binding.wrapIp, panel, yellow, barWidthPx, cutBr = cutInputBr)
        CutShapeHelper.applyWithLeftBar(binding.wrapPort, panel, yellow, barWidthPx, cutBr = cutInputBr)

        CutShapeHelper.applyWithLeftBar(
            binding.cardStatusOnline, colorCompat(R.color.cyan_12), cyan, barWidthPx, cutBl = cutM
        )
        CutShapeHelper.applyWithLeftBar(
            binding.cardStatusRefused, colorCompat(R.color.red_13), red, barWidthPx, cutBl = cutM
        )

        CutShapeHelper.applyButton(
            binding.btnConnect, yellow, colorCompat(R.color.yellow_30), cutTr = cutL, cutBl = cutL
        )
        CutShapeHelper.applyButton(binding.btnRetry, red, Color.WHITE, cutBr = dpF(11))

        @Suppress("DEPRECATION")
        binding.progressConnect.indeterminateDrawable?.setColorFilter(
            colorCompat(R.color.cp_on_yellow), PorterDuff.Mode.SRC_IN
        )

        startBeamAnimation()
    }

    private fun startBeamAnimation() {
        val card = binding.cardCar
        card.post {
            val w = card.width
            if (w > 0) {
                val lp = card.layoutParams
                lp.height = w * 300 / 750
                card.layoutParams = lp
            }
        }
        binding.lottieTarget.apply {
            setAnimation(sessionLottieRes)
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
        }
    }

    private fun setupInputs() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val wrap = if (v === binding.editHost) binding.wrapIp else binding.wrapPort
            val width = if (hasFocus) dp(4) else barWidthPx
            CutShapeHelper.applyWithLeftBar(
                wrap, colorCompat(R.color.cp_panel), colorCompat(R.color.cp_yellow), width, cutBr = cutInputBr
            )
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
            if (viewModel.onConnectClicked(host, port)) {
                hideKeyboard()
            }
        }

        binding.btnConnect.setOnTouchListener { _, event ->
            if (viewModel.uiState.value?.status == ConnectStatus.CONNECTING) {
                return@setOnTouchListener false
            }
            val yellow = colorCompat(R.color.cp_yellow)
            val pressed = colorCompat(R.color.cp_yellow_pressed)
            val strokeColor = colorCompat(R.color.yellow_30)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    CutShapeHelper.applyButton(binding.btnConnect, pressed, strokeColor, cutTr = cutL, cutBl = cutL)
                    binding.btnConnect.translationY = dpF(1)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    CutShapeHelper.applyButton(binding.btnConnect, yellow, strokeColor, cutTr = cutL, cutBl = cutL)
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
                setTextColor(colorCompat(R.color.cp_text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                alpha = 0.5f
            }
            binding.listRecent.addView(empty)
            return
        }

        for (host in addresses) {
            val row = TextView(this).apply {
                text = host
                setTextColor(colorCompat(R.color.cp_text_dim))
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
                CutShapeHelper.applyWithLeftBar(
                    binding.wrapIp,
                    colorCompat(R.color.cp_panel),
                    colorCompat(R.color.cp_red),
                    barWidthPx,
                    cutBr = cutInputBr
                )
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

        when (state.status) {
            ConnectStatus.IDLE -> {
                binding.textConnect.setText(R.string.cp_connect)
                CutShapeHelper.applyButton(
                    binding.btnConnect,
                    colorCompat(R.color.cp_yellow),
                    colorCompat(R.color.yellow_30),
                    cutTr = cutL,
                    cutBl = cutL
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
                binding.textStatusRefusedDesc.setText(
                    if (state.isTimeoutError) R.string.cp_status_timeout_desc else R.string.cp_status_refused_desc
                )
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

    override fun onDestroy() {
        binding.lottieTarget.cancelAnimation()
        if (isFinishing) {
            viewModel.onActivityFinishing()
        }
        super.onDestroy()
    }
}
