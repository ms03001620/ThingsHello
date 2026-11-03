package org.mark.mobile.ui.cyber;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** WiFi capsule: parallelogram, bottom-left and top-right shifted by offset. */
public class ParallelogramDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float offset;

    public ParallelogramDrawable(@ColorInt int color, float offsetPx) {
        paint.setColor(color);
        offset = offsetPx;
    }

    public void setColor(@ColorInt int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect b) {
        path.reset();
        path.moveTo(b.left + offset, b.top);
        path.lineTo(b.right, b.top);
        path.lineTo(b.right - offset, b.bottom);
        path.lineTo(b.left, b.bottom);
        path.close();
    }

    @Override public void draw(@NonNull Canvas c) { c.drawPath(path, paint); }
    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(@Nullable ColorFilter f) { paint.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
