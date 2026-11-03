package org.mark.mobile.ui.cyber

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.min

/**
 * Cyberpunk 45° 切角面板与按钮形状工具。
 */
object CutShapeHelper {

    fun panel(
        @ColorInt fillColor: Int,
        cutTl: Float = 0f,
        cutTr: Float = 0f,
        cutBr: Float = 0f,
        cutBl: Float = 0f
    ): CutDrawable = CutDrawable(fillColor, 0, 0f, cutTl, cutTr, cutBr, cutBl)

    fun apply(
        view: View,
        @ColorInt fillColor: Int,
        cutTl: Float = 0f,
        cutTr: Float = 0f,
        cutBr: Float = 0f,
        cutBl: Float = 0f
    ) {
        view.background = panel(fillColor, cutTl, cutTr, cutBr, cutBl)
    }

    fun applyWithLeftBar(
        view: View,
        @ColorInt fillColor: Int,
        @ColorInt barColor: Int,
        barWidthPx: Int,
        cutTl: Float = 0f,
        cutTr: Float = 0f,
        cutBr: Float = 0f,
        cutBl: Float = 0f
    ) {
        view.background = LeftBarCutDrawable(fillColor, barColor, barWidthPx, cutTl, cutTr, cutBr, cutBl)
    }

    fun applyButton(
        view: View,
        @ColorInt fillColor: Int,
        @ColorInt rippleColor: Int,
        cutTl: Float = 0f,
        cutTr: Float = 0f,
        cutBr: Float = 0f,
        cutBl: Float = 0f
    ) {
        val shape = panel(fillColor, cutTl, cutTr, cutBr, cutBl)
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), shape, shape)
    }

    fun stroked(
        @ColorInt fill: Int,
        @ColorInt stroke: Int,
        strokeW: Float,
        cutTl: Float = 0f,
        cutTr: Float = 0f,
        cutBr: Float = 0f,
        cutBl: Float = 0f
    ): CutDrawable = CutDrawable(fill, stroke, strokeW, cutTl, cutTr, cutBr, cutBl)

    fun states(normal: Drawable, pressed: Drawable): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_activated), pressed)
        addState(intArrayOf(), normal)
    }

    /** 遮盖视频右上角三角，模拟视频纹理视图的右上切角 */
    fun cornerMask(@ColorInt color: Int, cut: Float): Drawable = object : BaseDrawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val r = bounds.right.toFloat()
            val t = bounds.top.toFloat()
            path.reset()
            path.moveTo(r - cut, t)
            path.lineTo(r, t)
            path.lineTo(r, t + cut)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /** 遥测格背景：面板底色 + 顶部色条 */
    fun topBarPanel(@ColorInt fillColor: Int, @ColorInt barColor: Int, barHeight: Float): Drawable =
        object : BaseDrawable() {
            private val fill = Paint().apply { color = fillColor }
            private val bar = Paint().apply { color = barColor }

            override fun draw(canvas: Canvas) {
                canvas.drawRect(bounds, fill)
                canvas.drawRect(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.top + barHeight,
                    bar
                )
            }
        }

    /** 底部动作键背景：面板底色 + 右下切角 + 底边高亮线 */
    fun bottomEdgeCut(
        @ColorInt fillColor: Int,
        @ColorInt edgeColor: Int,
        edgeHeight: Float,
        cutW: Float,
        cutH: Float
    ): Drawable = object : BaseDrawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = edgeColor }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val l = bounds.left.toFloat()
            val t = bounds.top.toFloat()
            val r = bounds.right.toFloat()
            val b = bounds.bottom.toFloat()
            path.reset()
            path.moveTo(l, t)
            path.lineTo(r, t)
            path.lineTo(r, b - cutH)
            path.lineTo(r - cutW, b)
            path.lineTo(l, b)
            path.close()
            canvas.drawPath(path, fill)
            canvas.drawRect(l, b - edgeHeight, r - cutW, b, edge)
        }
    }

    abstract class BaseDrawable : Drawable() {
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class CutDrawable(
        @ColorInt fillColor: Int,
        @ColorInt private val strokeColor: Int,
        private val strokeWidth: Float,
        private val cutTl: Float,
        private val cutTr: Float,
        private val cutBr: Float,
        private val cutBl: Float
    ) : BaseDrawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = this@CutDrawable.strokeWidth
        }
        private val path = Path()

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            buildCutPath(path, bounds, strokeWidth / 2f, cutTl, cutTr, cutBr, cutBl)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, fillPaint)
            if (strokeWidth > 0f && Color.alpha(strokeColor) > 0) {
                canvas.drawPath(path, strokePaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
        }
    }

    class LeftBarCutDrawable(
        @ColorInt fillColor: Int,
        @ColorInt barColor: Int,
        barWidthPx: Int,
        private val cutTl: Float,
        private val cutTr: Float,
        private val cutBr: Float,
        private val cutBl: Float
    ) : BaseDrawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = barColor
        }
        private val barWidth = barWidthPx.coerceAtLeast(1)
        private val outer = Path()
        private val barPath = Path()

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            val halfMin = min(bounds.width(), bounds.height()) / 2f
            val tl = min(cutTl, halfMin)
            val bl = min(cutBl, halfMin)
            buildCutPath(outer, bounds, 0f, cutTl, cutTr, cutBr, cutBl)
            barPath.reset()
            barPath.addRect(
                bounds.left.toFloat(),
                bounds.top + tl,
                (bounds.left + barWidth).toFloat(),
                bounds.bottom - bl,
                Path.Direction.CW
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(outer, fillPaint)
            canvas.drawPath(barPath, barPaint)
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            barPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            barPaint.colorFilter = colorFilter
        }
    }

    private fun buildCutPath(
        path: Path,
        bounds: Rect,
        inset: Float,
        cutTl: Float,
        cutTr: Float,
        cutBr: Float,
        cutBl: Float
    ) {
        val l = bounds.left + inset
        val t = bounds.top + inset
        val r = bounds.right - inset
        val b = bounds.bottom - inset
        val halfMin = min(r - l, b - t) / 2f
        val tl = min(cutTl, halfMin)
        val tr = min(cutTr, halfMin)
        val br = min(cutBr, halfMin)
        val bl = min(cutBl, halfMin)

        path.reset()
        path.moveTo(l + tl, t)
        path.lineTo(r - tr, t)
        path.lineTo(r, t + tr)
        path.lineTo(r, b - br)
        path.lineTo(r - br, b)
        path.lineTo(l + bl, b)
        path.lineTo(l, b - bl)
        path.lineTo(l, t + tl)
        path.close()
    }
}
