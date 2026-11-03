package org.mark.mobile.ui.cyber

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import org.mark.mobile.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 0–100 赛博滑块：4dp 轨道 + 黄色发光填充 + 菱形滑块头。 */
class CyberSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val yellow = ContextCompat.getColor(context, R.color.cp_yellow)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cp_track)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yellow }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        maskFilter = BlurMaskFilter(8f * dp, BlurMaskFilter.Blur.NORMAL)
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cp_bg)
    }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val thumbPath = Path()
    private val thumbHalf = (8.0 * sqrt(2.0)).toFloat() * dp

    var value: Int = 50
        private set

    private var listener: ((value: Int, fromUser: Boolean) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setListener(l: (value: Int, fromUser: Boolean) -> Unit) {
        listener = l
    }

    fun setValue(newValue: Int, fromUser: Boolean) {
        val clamped = newValue.coerceIn(0, 100)
        if (clamped == value) return
        value = clamped
        invalidate()
        listener?.invoke(clamped, fromUser)
    }

    override fun onDraw(canvas: Canvas) {
        val left = thumbHalf
        val right = width - thumbHalf
        val cy = height / 2f
        val h = 2f * dp
        val x = left + (right - left) * value / 100f

        canvas.drawRect(left, cy - h, right, cy + h, trackPaint)
        canvas.drawRect(left, cy - h, x, cy + h, glowPaint)
        canvas.drawRect(left, cy - h, x, cy + h, fillPaint)

        val hd = thumbHalf - dp
        thumbPath.reset()
        thumbPath.moveTo(x, cy - hd)
        thumbPath.lineTo(x + hd, cy)
        thumbPath.lineTo(x, cy + hd)
        thumbPath.lineTo(x - hd, cy)
        thumbPath.close()
        canvas.drawPath(thumbPath, thumbFillPaint)
        canvas.drawPath(thumbPath, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val frac = (event.x - thumbHalf) / max(1f, width - 2f * thumbHalf)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setValue((frac * 100f).roundToInt(), true)
            }
            MotionEvent.ACTION_MOVE -> {
                setValue((frac * 100f).roundToInt(), true)
            }
        }
        return true
    }
}
