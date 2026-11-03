package org.mark.mobile.ui.cyber

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import org.mark.mobile.R
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 八角赛博摇杆：输出角度（0..359，0=右，90=前进）与推力（0..1）；松手时复位并回调 (-1, 0f)。
 */
class OctagonJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val yellow = ContextCompat.getColor(context, R.color.cp_yellow)

    private val outerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.yellow_05)
    }
    private val outerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FCEE0A
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        maskFilter = BlurMaskFilter(18f * dp, BlurMaskFilter.Blur.NORMAL)
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.yellow_30)
        strokeWidth = dp
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyan_60)
        style = Paint.Style.STROKE
        strokeWidth = dp
        pathEffect = DashPathEffect(floatArrayOf(4f * dp, 3f * dp), 0f)
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yellow }
    private val knobGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        maskFilter = BlurMaskFilter(20f * dp, BlurMaskFilter.Blur.OUTER)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.yellow_60)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    private val octoPath = Path()
    private val knobPath = Path()
    private var kx = 0f
    private var ky = 0f
    private var listener: ((angle: Int, power: Float) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setListener(l: (angle: Int, power: Float) -> Unit) {
        listener = l
    }

    private fun boxSize(): Float = min(width, height) - 2f * dp

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = boxSize()

        buildOctagon(octoPath, cx, cy, s, 0.25f)
        canvas.drawPath(octoPath, outerFill)
        canvas.drawPath(octoPath, outerGlow)
        canvas.drawPath(octoPath, outerStroke)

        canvas.drawLine(cx, cy - s / 2f, cx, cy + s / 2f, cross)
        canvas.drawLine(cx - s / 2f, cy, cx + s / 2f, cy, cross)
        canvas.drawCircle(cx, cy, s * 57f / 190f, ring)

        val fontOff = 16f * dp
        canvas.drawText("前进", cx, cy - s / 2f + fontOff + label.textSize * 0.4f, label)
        canvas.drawText("后退", cx, cy + s / 2f - fontOff + label.textSize * 0.4f, label)

        buildOctagon(knobPath, cx + kx, cy + ky, s * 64f / 190f, 0.30f)
        canvas.drawPath(knobPath, knobGlow)
        canvas.drawPath(knobPath, knob)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val cx = width / 2f
        val cy = height / 2f
        val s = boxSize()
        val maxDist = (s - s * 64f / 190f) / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                handleMove(event.x - cx, event.y - cy, maxDist)
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event.x - cx, event.y - cy, maxDist)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                kx = 0f
                ky = 0f
                invalidate()
                listener?.invoke(-1, 0f)
            }
        }
        return true
    }

    private fun handleMove(rawDx: Float, rawDy: Float, maxDist: Float) {
        var dx = rawDx
        var dy = rawDy
        var d = hypot(dx, dy)
        if (d > maxDist && d > 0f) {
            dx = dx * maxDist / d
            dy = dy * maxDist / d
            d = maxDist
        }
        kx = dx
        ky = dy
        invalidate()

        var angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).roundToInt()
        if (angle < 0) angle += 360
        val power = if (maxDist > 0f) d / maxDist else 0f
        listener?.invoke(angle, power)
    }

    private fun buildOctagon(p: Path, cx: Float, cy: Float, size: Float, cutFrac: Float) {
        val h = size / 2f
        val cut = size * cutFrac
        p.reset()
        p.moveTo(cx - h + cut, cy - h)
        p.lineTo(cx + h - cut, cy - h)
        p.lineTo(cx + h, cy - h + cut)
        p.lineTo(cx + h, cy + h - cut)
        p.lineTo(cx + h - cut, cy + h)
        p.lineTo(cx - h + cut, cy + h)
        p.lineTo(cx - h, cy + h - cut)
        p.lineTo(cx - h, cy - h + cut)
        p.close()
    }
}
