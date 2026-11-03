package org.mark.mobile.ui.cyber

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import org.mark.mobile.R

/** 垂直居中的全宽 2dp 青色扫描光束（带 12dp 发光）。 */
class GlowLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val thick = 2f * dp
    private val cyan = ContextCompat.getColor(context, R.color.cp_cyan)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        maskFilter = BlurMaskFilter(12f * dp, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val w = width.toFloat()
        canvas.drawRect(0f, cy - thick / 2f, w, cy + thick / 2f, glowPaint)
        canvas.drawRect(0f, cy - thick / 2f, w, cy + thick / 2f, linePaint)
    }
}
