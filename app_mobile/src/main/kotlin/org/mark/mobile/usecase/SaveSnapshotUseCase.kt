package org.mark.mobile.usecase

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SnapshotResult {
    NO_FRAME,
    NEED_STORAGE_PERMISSION,
    SAVED,
    FAILED
}

interface ISnapshotStore {
    fun hasWritePermission(): Boolean
    fun saveToGallery(bitmap: Bitmap): Uri?
}

/**
 * 通过 MediaStore 将视频帧 JPEG 保存至相册 Pictures/ThingsHello。
 */
class MediaStoreSnapshotStore(context: Context) : ISnapshotStore {
    private val appContext = context.applicationContext

    override fun hasWritePermission(): Boolean {
        return Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun saveToGallery(bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "ThingsHello_$timestamp.jpg"
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put("relative_path", "Pictures/ThingsHello")
                put("is_pending", 1)
            }
        }
        var uri: Uri? = null
        return try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)) {
                    throw IOException("compress failed")
                }
            } ?: throw IOException("openOutputStream returned null")

            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put("is_pending", 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.e("SaveSnapshotUseCase", "saveToGallery failed", e)
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            null
        }
    }
}

/**
 * 统一处理遥控页小窗与全屏预览页的视频截图保存用例。
 */
class SaveSnapshotUseCase(
    private val store: ISnapshotStore
) {
    operator fun invoke(bitmap: Bitmap?): SnapshotResult {
        if (bitmap == null) return SnapshotResult.NO_FRAME
        if (!store.hasWritePermission()) return SnapshotResult.NEED_STORAGE_PERMISSION
        return if (store.saveToGallery(bitmap) != null) SnapshotResult.SAVED else SnapshotResult.FAILED
    }
}
