package org.mark.mobile.ui.cyber;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.mark.mobile.R;

/**
 * Display title with RGB chromatic aberration (red +3dp / cyan -3dp) then yellow body.
 */
public class GlitchTextView extends AppCompatTextView {
    private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float offsetPx;

    public GlitchTextView(Context context) {
        super(context);
        init();
    }

    public GlitchTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlitchTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        offsetPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics());
        setTypeface(ResourcesCompat.getFont(getContext(), R.font.orbitron_black));
        setTextColor(ContextCompat.getColor(getContext(), R.color.cp_yellow));
        redPaint.set(getPaint());
        cyanPaint.set(getPaint());
        redPaint.setColor(ContextCompat.getColor(getContext(), R.color.cp_red));
        cyanPaint.setColor(ContextCompat.getColor(getContext(), R.color.cp_cyan));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CharSequence text = getText();
        if (text == null || text.length() == 0) {
            super.onDraw(canvas);
            return;
        }
        float x = getTotalPaddingLeft();
        float y = getBaseline();
        // Update shadow paints with current text size / typeface
        redPaint.set(getPaint());
        cyanPaint.set(getPaint());
        redPaint.setColor(ContextCompat.getColor(getContext(), R.color.cp_red));
        cyanPaint.setColor(ContextCompat.getColor(getContext(), R.color.cp_cyan));
        canvas.drawText(text, 0, text.length(), x + offsetPx, y, redPaint);
        canvas.drawText(text, 0, text.length(), x - offsetPx, y, cyanPaint);
        super.onDraw(canvas);
    }
}
