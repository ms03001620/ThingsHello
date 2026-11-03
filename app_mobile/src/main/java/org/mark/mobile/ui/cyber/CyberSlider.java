package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
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
        void onStartTracking();
        void onStopTracking();
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable thumb;
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
        thumb = ContextCompat.getDrawable(c, R.drawable.ic_slider_thumb_diamond);
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

    private float thumbHalf() { return 8 * dp; }

    @Override
    protected void onDraw(Canvas c) {
        float half = thumbHalf();
        float left = half, right = getWidth() - half, cy = getHeight() / 2f, h = 2 * dp;
        float x = left + (right - left) * value / 100f;
        c.drawRect(left, cy - h, right, cy + h, track);
        c.drawRect(left, cy - h, x, cy + h, glow);
        c.drawRect(left, cy - h, x, cy + h, fill);
        if (thumb != null) {
            thumb.setBounds((int) (x - half), (int) (cy - half), (int) (x + half), (int) (cy + half));
            thumb.draw(c);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        float half = thumbHalf();
        float frac = (e.getX() - half) / Math.max(1f, getWidth() - 2 * half);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onStartTracking();
                setValue(Math.round(frac * 100), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                setValue(Math.round(frac * 100), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onStopTracking();
                return true;
            default:
                return true;
        }
    }
}
