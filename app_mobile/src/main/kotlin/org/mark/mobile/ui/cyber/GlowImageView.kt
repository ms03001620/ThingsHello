package org.mark.mobile.ui.cyber

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import org.mark.mobile.R

/** 根据图标 Alpha 轮廓绘制 6dp 青色外发光的 ImageView。 */
class GlowImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val radius = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
    )
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cp_cyan)
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.OUTER)
    }
    private var glowBitmap: Bitmap? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        if (drawable != null && width > 0 && height > 0) {
            val alphaMask = glowBitmap ?: run {
                val src = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                super.onDraw(Canvas(src))
                src.extractAlpha().also {
                    src.recycle()
                    glowBitmap = it
                }
            }
            canvas.drawBitmap(alphaMask, 0f, 0f, glowPaint)
        }
        super.onDraw(canvas)
    }
}
