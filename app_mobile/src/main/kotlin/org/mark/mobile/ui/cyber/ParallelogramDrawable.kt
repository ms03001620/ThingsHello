package org.mark.mobile.ui.cyber

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/** WiFi 胶囊背景：左下与右上按 offset 倾斜的平行四边形。 */
class ParallelogramDrawable(
    @ColorInt color: Int,
    private val offsetPx: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    fun setColor(@ColorInt color: Int) {
        paint.color = color
        invalidateSelf()
    }

    override fun onBoundsChange(b: Rect) {
        path.reset()
        path.moveTo(b.left + offsetPx, b.top.toFloat())
        path.lineTo(b.right.toFloat(), b.top.toFloat())
        path.lineTo(b.right - offsetPx, b.bottom.toFloat())
        path.lineTo(b.left.toFloat(), b.bottom.toFloat())
        path.close()
    }

    override fun draw(canvas: Canvas) = canvas.drawPath(path, paint)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
