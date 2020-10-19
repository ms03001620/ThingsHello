package org.mark.mobile.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.mark.base.CameraUtils;

import androidx.annotation.WorkerThread;

/**
 * Created by mark on 2020/10/19
 */

public class BitmapSurfaceView extends SurfaceView {

    private SurfaceHolder surfaceHolder;

    private volatile boolean isSurfaceCreated;

    private Paint paint = new Paint();

    private volatile float degrees;

    public BitmapSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceCreated = true;
                Log.w("BitmapSurfaceView", "surfaceCreated");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                isSurfaceCreated = false;
                r1 = null;
                r2 = null;
                Log.w("BitmapSurfaceView", "surfaceDestroyed");
            }
        });
        setFocusable(true);
        setKeepScreenOn(true);
        setFocusableInTouchMode(true);
    }

    public void setDegrees(float degrees) {
        this.degrees = degrees;
    }

    Rect r1, r2;

    @WorkerThread
    public void draw(Bitmap bitmap) {
        if (!isSurfaceCreated) {
            Log.w("BitmapSurfaceView", "surface not created!");
            return;
        }
        if (r1 == null) {
            r1 = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }
        if (r2 == null && getWidth() != 0) {
            r2 = new Rect(0, 0, getWidth(), getHeight());
        }
        if (degrees != 0) {
            bitmap = CameraUtils.createFromBytes(degrees, bitmap);
        }
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            canvas.drawBitmap(bitmap, r1, r2, paint);
        } catch (Exception e) {
            Log.e("BitmapSurfaceView", "draw", e);
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
