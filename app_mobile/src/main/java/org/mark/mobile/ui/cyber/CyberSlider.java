package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.mark.mobile.R;

/** 0–100 slider: 4dp track (#2A2A3A), yellow glowing fill, diamond thumb. */
public class CyberSlider extends View {
    public interface Listener {
        void onValueChanged(int value, boolean fromUser);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path thumbPath = new android.graphics.Path();
    private final float dp;
    private int value = 50;
    private Listener listener;

    public CyberSlider(Context c, AttributeSet a) {
        super(c, a);
        dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        track.setColor(ContextCompat.getColor(c, R.color.cp_track));
        int y = ContextCompat.getColor(c, R.color.cp_yellow);
        fill.setColor(y);
        glow.setColor(y);
        glow.setMaskFilter(new BlurMaskFilter(8 * dp, BlurMaskFilter.Blur.NORMAL));
        thumbFill.setColor(ContextCompat.getColor(c, R.color.cp_bg));
        thumbStroke.setColor(y);
        thumbStroke.setStyle(Paint.Style.STROKE);
        thumbStroke.setStrokeWidth(2 * dp);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) { listener = l; }
    public int getValue() { return value; }

    public void setValue(int v, boolean fromUser) {
        v = Math.max(0, Math.min(100, v));
        if (v == value) return;
        value = v;
        invalidate();
        if (listener != null) listener.onValueChanged(v, fromUser);
    }

    /** 16dp square rotated 45° → half-diagonal 8√2 ≈ 11.3dp (outer edge incl. stroke). */
    private float thumbHalf() { return (float) (8 * Math.sqrt(2)) * dp; }

    @Override
    protected void onDraw(Canvas c) {
        float half = thumbHalf();
        float left = half, right = getWidth() - half, cy = getHeight() / 2f, h = 2 * dp;
        float x = left + (right - left) * value / 100f;
        c.drawRect(left, cy - h, right, cy + h, track);
        c.drawRect(left, cy - h, x, cy + h, glow);
        c.drawRect(left, cy - h, x, cy + h, fill);
        float hd = half - dp; // stroke centred inside the 22.6dp diagonal
        thumbPath.reset();
        thumbPath.moveTo(x, cy - hd);
        thumbPath.lineTo(x + hd, cy);
        thumbPath.lineTo(x, cy + hd);
        thumbPath.lineTo(x - hd, cy);
        thumbPath.close();
        c.drawPath(thumbPath, thumbFill);
        c.drawPath(thumbPath, thumbStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        float half = thumbHalf();
        float frac = (e.getX() - half) / Math.max(1f, getWidth() - 2 * half);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                setValue(Math.round(frac * 100), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                setValue(Math.round(frac * 100), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }
}
