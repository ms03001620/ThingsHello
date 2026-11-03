package org.mark.mobile.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.Nullable;

/** Saves a video frame to Pictures/ThingsHello via MediaStore. */
public final class GallerySaver {
    private GallerySaver() { }

    @Nullable
    public static Uri save(Context ctx, Bitmap bmp) {
        String name = "ThingsHello_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put("relative_path", "Pictures/ThingsHello"); // MediaStore.MediaColumns.RELATIVE_PATH
            v.put("is_pending", 1);
        }
        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null || !bmp.compress(Bitmap.CompressFormat.JPEG, 92, os)) throw new java.io.IOException("write");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put("is_pending", 0);
                cr.update(uri, done, null, null);
            }
            return uri;
        } catch (Exception e) {
            Log.e("GallerySaver", "save", e);
            if (uri != null) cr.delete(uri, null, null);
            return null;
        }
    }
}
