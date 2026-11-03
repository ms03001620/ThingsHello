package org.mark.mobile.ui.cyber

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue

/** 水平扫描线覆盖层：每 3dp 绘制一条 1dp 细线。 */
class ScanlineDrawable(res: Resources, color: Int) : Drawable() {

    private val paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val stepPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, res.displayMetrics)
    private val linePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, res.displayMetrics)

    override fun draw(canvas: Canvas) {
        val left = bounds.left.toFloat()
        val right = (bounds.left + bounds.width()).toFloat()
        val bottom = bounds.bottom.toFloat()
        var y = bounds.top.toFloat()
        while (y < bottom) {
            canvas.drawRect(left, y, right, y + linePx, paint)
            y += stepPx
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
