package org.mark.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("org.mark.lib_base.test", appContext.getPackageName());
    }

    @Test
    public void zoomImage() {
        Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.parseColor("#FF0000"));

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();


        Bitmap target = CameraUtils.zoomImage(bitmap, 100, 100);


        Assert.assertEquals(target.getWidth(), 100);

        Assert.assertEquals(target.getHeight(), 100);

    }
}
