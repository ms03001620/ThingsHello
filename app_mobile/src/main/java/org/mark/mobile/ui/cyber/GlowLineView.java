package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.mark.mobile.R;

/** Full-width 2dp cyan scan line with 12dp glow, vertically centered in the view. */
public class GlowLineView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float thick;

    public GlowLineView(Context c, AttributeSet a) {
        super(c, a);
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, getResources().getDisplayMetrics());
        thick = 2 * dp;
        int cyan = ContextCompat.getColor(c, R.color.cp_cyan);
        line.setColor(cyan);
        glow.setColor(cyan);
        glow.setMaskFilter(new BlurMaskFilter(12 * dp, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        canvas.drawRect(0, cy - thick / 2, getWidth(), cy + thick / 2, glow);
        canvas.drawRect(0, cy - thick / 2, getWidth(), cy + thick / 2, line);
    }
}
