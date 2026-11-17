package app.frame.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A photo opened for editing.
 *
 * [preview] is an upright (EXIF-corrected), down-sampled copy used for on-screen display,
 * eyedropper sampling and palette analysis. The full-resolution pixels are NOT kept in
 * memory; the exporter re-reads them from [uri] so large photos can't exhaust the heap
 * while the user is just editing.
 */
class LoadedImage(
    val uri: Uri,
    val displayName: String?,
    val preview: Bitmap,
    /** Upright dimensions of the ORIGINAL photograph (what layout math uses). */
    val width: Int,
    val height: Int,
    val rawWidth: Int,
    val rawHeight: Int,
    val exifOrientation: Int,
)

object ImageLoader {

    const val MAX_PREVIEW_SIDE = 2048

    suspend fun load(context: Context, uri: Uri): LoadedImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) throw IllegalArgumentException("This file isn't a readable image.")

        val exif = readOrientation(context, uri)

        var sample = 1
        while (maxOf(rawW, rawH) / sample > MAX_PREVIEW_SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Couldn't decode this image.")

        val upright = if (exif == ExifInterface.ORIENTATION_NORMAL || exif == ExifInterface.ORIENTATION_UNDEFINED) {
            decoded
        } else {
            val m = Orientation.matrix(exif, decoded.width, decoded.height)
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, false)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }

        val swap = Orientation.swapsAxes(exif)
        LoadedImage(
            uri = uri,
            displayName = queryName(context, uri),
            preview = upright,
            width = if (swap) rawH else rawW,
            height = if (swap) rawW else rawH,
            rawWidth = rawW,
            rawHeight = rawH,
            exifOrientation = exif,
        )
    }

    private fun readOrientation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun queryName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}
