package org.mark.mobile.ui.cyber

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import org.mark.mobile.R

/**
 * RGB 色差错位标题文本（红 +3dp / 青 -3dp 残影 + 黄色主字）。
 */
class GlitchTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val offsetPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics
    )
    private val redColor = ContextCompat.getColor(context, R.color.cp_red)
    private val cyanColor = ContextCompat.getColor(context, R.color.cp_cyan)

    init {
        typeface = ResourcesCompat.getFont(context, R.font.orbitron_black)
        setTextColor(ContextCompat.getColor(context, R.color.cp_yellow))
    }

    override fun onDraw(canvas: Canvas) {
        val currentText = text
        if (currentText.isNullOrEmpty()) {
            super.onDraw(canvas)
            return
        }
        val x = totalPaddingLeft.toFloat()
        val y = baseline.toFloat()

        redPaint.set(paint)
        redPaint.color = redColor
        cyanPaint.set(paint)
        cyanPaint.color = cyanColor

        canvas.drawText(currentText, 0, currentText.length, x + offsetPx, y, redPaint)
        canvas.drawText(currentText, 0, currentText.length, x - offsetPx, y, cyanPaint)
        super.onDraw(canvas)
    }
}
