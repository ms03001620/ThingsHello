package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import org.mark.mobile.R;

/** ImageView with a cyan outer glow (default 6dp) drawn from the drawable's alpha. */
public class GlowImageView extends AppCompatImageView {
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap glow;
    private final float radius;

    public GlowImageView(Context c, AttributeSet a) {
        super(c, a);
        radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, getResources().getDisplayMetrics());
        glowPaint.setColor(ContextCompat.getColor(c, R.color.cp_cyan));
        glowPaint.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.OUTER));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glow = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable d = getDrawable();
        if (d != null && getWidth() > 0 && getHeight() > 0) {
            if (glow == null) {
                Bitmap src = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas sc = new Canvas(src);
                super.onDraw(sc);
                glow = src.extractAlpha();
                src.recycle();
            }
            canvas.drawBitmap(glow, 0, 0, glowPaint);
        }
        super.onDraw(canvas);
    }
}
