package org.mark.mobile.ui.cyber;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Cyberpunk cut-corner panels (45°) without Material dependency — Path equivalent of
 * ShapeAppearanceModel CornerFamily.CUT.
 */
public final class CutShapeHelper {
    private CutShapeHelper() {
    }

    public static CutDrawable panel(
            @ColorInt int fillColor,
            float cutTl, float cutTr, float cutBr, float cutBl) {
        return new CutDrawable(fillColor, 0, 0, cutTl, cutTr, cutBr, cutBl);
    }

    public static void apply(
            View view,
            @ColorInt int fillColor,
            float cutTl, float cutTr, float cutBr, float cutBl) {
        view.setBackground(panel(fillColor, cutTl, cutTr, cutBr, cutBl));
    }

    public static void applyWithLeftBar(
            View view,
            @ColorInt int fillColor,
            @ColorInt int barColor,
            int barWidthPx,
            float cutTl, float cutTr, float cutBr, float cutBl) {
        view.setBackground(new LeftBarCutDrawable(
                fillColor, barColor, barWidthPx, cutTl, cutTr, cutBr, cutBl));
    }

    public static void applyButton(
            View view,
            @ColorInt int fillColor,
            @ColorInt int rippleColor,
            float cutTl, float cutTr, float cutBr, float cutBl) {
        CutDrawable shape = panel(fillColor, cutTl, cutTr, cutBr, cutBl);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(rippleColor), shape, shape);
        view.setBackground(ripple);
    }

    public static void setLeftBarColor(View view, @ColorInt int barColor) {
        Drawable bg = view.getBackground();
        if (bg instanceof LeftBarCutDrawable) {
            ((LeftBarCutDrawable) bg).setBarColor(barColor);
            view.invalidate();
        }
    }

    public static void setLeftBarAndFill(View view, @ColorInt int fill, @ColorInt int bar) {
        Drawable bg = view.getBackground();
        if (bg instanceof LeftBarCutDrawable) {
            LeftBarCutDrawable d = (LeftBarCutDrawable) bg;
            d.setFillColor(fill);
            d.setBarColor(bar);
            view.invalidate();
        }
    }

    /** Single-fill cut rectangle. */
    public static class CutDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float cutTl, cutTr, cutBr, cutBl;
        private int strokeColor;
        private float strokeWidth;

        public CutDrawable(
                @ColorInt int fillColor,
                @ColorInt int strokeColor,
                float strokeWidth,
                float cutTl, float cutTr, float cutBr, float cutBl) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(strokeColor);
            strokePaint.setStrokeWidth(strokeWidth);
            this.cutTl = cutTl;
            this.cutTr = cutTr;
            this.cutBr = cutBr;
            this.cutBl = cutBl;
        }

        public void setFillColor(@ColorInt int color) {
            fillPaint.setColor(color);
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rebuild(bounds);
        }

        private void rebuild(Rect bounds) {
            path.reset();
            float l = bounds.left;
            float t = bounds.top;
            float r = bounds.right;
            float b = bounds.bottom;
            float w = r - l;
            float h = b - t;
            float tl = Math.min(cutTl, Math.min(w, h) / 2f);
            float tr = Math.min(cutTr, Math.min(w, h) / 2f);
            float br = Math.min(cutBr, Math.min(w, h) / 2f);
            float bl = Math.min(cutBl, Math.min(w, h) / 2f);
            // Clockwise from top-left after cut
            path.moveTo(l + tl, t);
            path.lineTo(r - tr, t);
            path.lineTo(r, t + tr);
            path.lineTo(r, b - br);
            path.lineTo(r - br, b);
            path.lineTo(l + bl, b);
            path.lineTo(l, b - bl);
            path.lineTo(l, t + tl);
            path.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(path, fillPaint);
            if (strokeWidth > 0f && Color.alpha(strokeColor) > 0) {
                canvas.drawPath(path, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

    }

    /** Fill + left accent bar sharing the same outer cut path. */
    public static class LeftBarCutDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outer = new Path();
        private final Path barPath = new Path();
        private final int barWidthPx;
        private final float cutTl, cutTr, cutBr, cutBl;

        LeftBarCutDrawable(
                @ColorInt int fillColor,
                @ColorInt int barColor,
                int barWidthPx,
                float cutTl, float cutTr, float cutBr, float cutBl) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(barColor);
            this.barWidthPx = Math.max(1, barWidthPx);
            this.cutTl = cutTl;
            this.cutTr = cutTr;
            this.cutBr = cutBr;
            this.cutBl = cutBl;
        }

        void setFillColor(@ColorInt int color) {
            fillPaint.setColor(color);
            invalidateSelf();
        }

        void setBarColor(@ColorInt int color) {
            barPaint.setColor(color);
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rebuild(bounds);
        }

        private void rebuild(Rect bounds) {
            float l = bounds.left;
            float t = bounds.top;
            float r = bounds.right;
            float b = bounds.bottom;
            float w = r - l;
            float h = b - t;
            float tl = Math.min(cutTl, Math.min(w, h) / 2f);
            float tr = Math.min(cutTr, Math.min(w, h) / 2f);
            float br = Math.min(cutBr, Math.min(w, h) / 2f);
            float bl = Math.min(cutBl, Math.min(w, h) / 2f);

            outer.reset();
            outer.moveTo(l + tl, t);
            outer.lineTo(r - tr, t);
            outer.lineTo(r, t + tr);
            outer.lineTo(r, b - br);
            outer.lineTo(r - br, b);
            outer.lineTo(l + bl, b);
            outer.lineTo(l, b - bl);
            outer.lineTo(l, t + tl);
            outer.close();

            float barR = l + barWidthPx;
            barPath.reset();
            barPath.moveTo(l + tl, t);
            barPath.lineTo(barR, t);
            barPath.lineTo(barR, b);
            barPath.lineTo(l + bl, b);
            barPath.lineTo(l, b - bl);
            barPath.lineTo(l, t + tl);
            barPath.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(outer, fillPaint);
            canvas.drawPath(barPath, barPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            barPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            barPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
