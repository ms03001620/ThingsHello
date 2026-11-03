package org.mark.mobile.ui.cyber;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Horizontal scanlines every 3dp, 1dp thick. */
public class ScanlineDrawable extends Drawable {
    private final Paint paint = new Paint();
    private final float stepPx;
    private final float linePx;

    public ScanlineDrawable(Resources res, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        stepPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, res.getDisplayMetrics());
        linePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, res.getDisplayMetrics());
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float bottom = getBounds().bottom;
        float width = getBounds().width();
        for (float y = getBounds().top; y < bottom; y += stepPx) {
            canvas.drawRect(getBounds().left, y, getBounds().left + width, y + linePx, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
