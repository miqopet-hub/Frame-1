package app.frame.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.frame.domain.BorderSettings
import app.frame.domain.ColorMath
import app.frame.domain.FrameLayout
import app.frame.domain.LayoutCalculator
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
}

sealed interface ExportResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val width: Int,
        val height: Int,
        /** True only if the canvas had to be reduced to fit in memory. */
        val reducedForMemory: Boolean,
    ) : ExportResult

    data class Failure(val message: String) : ExportResult
}

object FrameExporter {

    const val FOLDER = "FRAME"
    const val JPEG_QUALITY = 95

    /** Progressive fall-backs if the device cannot allocate the full-size canvas. */
    private val RETRY_FACTORS = floatArrayOf(1f, 0.75f, 0.5f, 0.35f)

    fun export(
        context: Context,
        image: LoadedImage,
        settings: BorderSettings,
        borderColor: Int,
        format: ExportFormat,
    ): ExportResult {
        val fullLayout = LayoutCalculator.compute(image.width, image.height, settings)
        val lineColor = ColorMath.resolveLineColor(settings.doubleLines.colorMode, borderColor)
        val baseScale = LayoutCalculator.exportScaleFor(fullLayout)

        var rendered: Bitmap? = null
        var usedLayout: FrameLayout = fullLayout
        var lastError: Throwable? = null

        for (factor in RETRY_FACTORS) {
            val scale = baseScale * factor
            val layout = fullLayout.scaled(scale)
            try {
                rendered = renderAt(context, image, layout, borderColor, lineColor)
                usedLayout = layout
                break
            } catch (e: OutOfMemoryError) {
                lastError = e
            } catch (e: Exception) {
                return ExportResult.Failure("Couldn't render the image: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        val bitmap = rendered
            ?: return ExportResult.Failure(
                "Not enough memory to export this photo" +
                    (lastError?.let { " even at reduced size" } ?: "") + ".",
            )

        return try {
            val saved = save(context, bitmap, format)
            ExportResult.Success(
                uri = saved.first,
                fileName = saved.second,
                width = usedLayout.canvasWidth,
                height = usedLayout.canvasHeight,
                reducedForMemory = usedLayout.canvasWidth != fullLayout.canvasWidth ||
                    usedLayout.canvasHeight != fullLayout.canvasHeight,
            )
        } catch (e: Exception) {
            ExportResult.Failure("Couldn't save the image: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderAt(
        context: Context,
        image: LoadedImage,
        layout: FrameLayout,
        borderColor: Int,
        lineColor: Int,
    ): Bitmap {
        // Decode the original at full resolution unless the canvas was reduced, in which
        // case decode at the largest power-of-two subsample that is still >= the target size
        // (so we never upscale, and never decode more pixels than needed).
        val target = max(layout.photo.width, layout.photo.height)
        var sample = 1
        while (max(image.rawWidth, image.rawHeight) / (sample * 2) >= target) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = context.contentResolver.openInputStream(image.uri).use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("The original file could not be read again.")

        try {
            return FrameRenderer.render(source, image.exifOrientation, layout, borderColor, lineColor)
        } finally {
            source.recycle()
        }
    }

    /** @return Pair(uri, displayName) */
    private fun save(context: Context, bitmap: Bitmap, format: ExportFormat): Pair<Uri, String> {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "FRAME_$stamp.${format.extension}"
        val compressFormat = if (format == ExportFormat.JPEG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val quality = if (format == ExportFormat.JPEG) JPEG_QUALITY else 100

        fun write(out: OutputStream) {
            if (!bitmap.compress(compressFormat, quality, out)) throw IllegalStateException("Encoder failed")
            out.flush()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore refused the new file.")
            try {
                resolver.openOutputStream(uri)?.use { write(it) }
                    ?: throw IllegalStateException("Couldn't open the output file.")
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                uri to name
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Couldn't create Pictures/$FOLDER.")
            val file = File(dir, name)
            FileOutputStream(file).use { write(it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mime), null)
            Uri.fromFile(file) to name
        }
    }
}
