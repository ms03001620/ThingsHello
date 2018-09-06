package org.mark.base;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by Mark on 2018/9/6
 */
@RunWith(AndroidJUnit4.class)
public class CameraUtilsTest {

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