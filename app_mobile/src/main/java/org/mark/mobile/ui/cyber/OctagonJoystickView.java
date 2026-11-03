package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.mark.mobile.R;

/**
 * Octagon joystick per spec 6b. Emits angle (0–359, 0=right, 90=forward) and power 0..1;
 * on release emits power 0 with angle -1 and recentres the knob.
 */
public class OctagonJoystickView extends View {
    public interface Listener {
        void onMove(int angle, float power);
    }

    private final Paint outerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path octo = new Path();
    private final Path knobPath = new Path();
    private final float dp;
    private float kx, ky; // knob offset from centre
    private Listener listener;

    public OctagonJoystickView(Context c, AttributeSet a) {
        super(c, a);
        dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        int y = ContextCompat.getColor(c, R.color.cp_yellow);
        outerFill.setColor(ContextCompat.getColor(c, R.color.yellow_05));
        outerStroke.setColor(y);
        outerStroke.setStyle(Paint.Style.STROKE);
        outerStroke.setStrokeWidth(2 * dp);
        outerGlow.setColor(0x59FCEE0A);
        outerGlow.setStyle(Paint.Style.STROKE);
        outerGlow.setStrokeWidth(2 * dp);
        outerGlow.setMaskFilter(new BlurMaskFilter(18 * dp, BlurMaskFilter.Blur.NORMAL));
        cross.setColor(ContextCompat.getColor(c, R.color.yellow_30));
        cross.setStrokeWidth(dp);
        ring.setColor(ContextCompat.getColor(c, R.color.cyan_60));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp);
        ring.setPathEffect(new DashPathEffect(new float[]{4 * dp, 3 * dp}, 0));
        knob.setColor(y);
        knobGlow.setColor(y);
        knobGlow.setMaskFilter(new BlurMaskFilter(20 * dp, BlurMaskFilter.Blur.OUTER));
        label.setColor(ContextCompat.getColor(c, R.color.yellow_60));
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
        label.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) { listener = l; }

    private static void octagon(Path p, float cx, float cy, float size, float cutFrac) {
        float h = size / 2f, cut = size * cutFrac;
        p.reset();
        p.moveTo(cx - h + cut, cy - h);
        p.lineTo(cx + h - cut, cy - h);
        p.lineTo(cx + h, cy - h + cut);
        p.lineTo(cx + h, cy + h - cut);
        p.lineTo(cx + h - cut, cy + h);
        p.lineTo(cx - h + cut, cy + h);
        p.lineTo(cx - h, cy + h - cut);
        p.lineTo(cx - h, cy - h + cut);
        p.close();
    }

    private float size() { return Math.min(getWidth(), getHeight()) - 2 * dp; }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, s = size();
        octagon(octo, cx, cy, s, 0.25f);
        c.drawPath(octo, outerFill);
        c.drawPath(octo, outerGlow);
        c.drawPath(octo, outerStroke);
        c.drawLine(cx, cy - s / 2, cx, cy + s / 2, cross);
        c.drawLine(cx - s / 2, cy, cx + s / 2, cy, cross);
        c.drawCircle(cx, cy, s * 57f / 190f, ring);
        float fontOff = 16 * dp;
        c.drawText("前进", cx, cy - s / 2 + fontOff + label.getTextSize() * 0.4f, label);
        c.drawText("后退", cx, cy + s / 2 - fontOff + label.getTextSize() * 0.4f, label);
        float ks = s * 64f / 190f;
        octagon(knobPath, cx + kx, cy + ky, ks, 0.30f);
        c.drawPath(knobPath, knobGlow);
        c.drawPath(knobPath, knob);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = (size() - size() * 64f / 190f) / 2f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - cx, dy = e.getY() - cy;
                float d = (float) Math.hypot(dx, dy);
                if (d > max && d > 0) { dx = dx * max / d; dy = dy * max / d; d = max; }
                kx = dx; ky = dy;
                invalidate();
                int angle = (int) Math.round(Math.toDegrees(Math.atan2(-dy, dx)));
                if (angle < 0) angle += 360;
                float power = max > 0 ? d / max : 0;
                if (listener != null) listener.onMove(angle, power);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                kx = 0; ky = 0;
                invalidate();
                if (listener != null) listener.onMove(-1, 0f);
                return true;
            default:
                return true;
        }
    }
}
