package org.mark.mobile.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.WorkerThread;

/**
 * Created by mark on 2020/10/19
 */

public class BitmapSurfaceView extends SurfaceView {

    private SurfaceHolder surfaceHolder;

    private volatile boolean isSurfaceCreated;

    private Paint paint = new Paint();

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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        System.out.println("w:"+getWidth()+", h:"+getHeight());
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
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            //canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, r1, r2, paint);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("BitmapSurfaceView", "draw", e);
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
