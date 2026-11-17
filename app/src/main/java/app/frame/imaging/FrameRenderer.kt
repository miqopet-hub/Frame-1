package app.frame.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import app.frame.domain.FrameLayout

/**
 * Draws a [FrameLayout] into a new bitmap. The photograph is placed with a matrix that
 * contains only the EXIF rotation/flip plus (when the export was memory-reduced) a
 * uniform scale. At full size the scale is exactly 1, so source pixels are copied 1:1.
 */
object FrameRenderer {

    fun render(
        source: Bitmap,
        exif: Int,
        layout: FrameLayout,
        borderColor: Int,
        lineColor: Int,
    ): Bitmap {
        val out = createCanvasBitmap(layout.canvasWidth, layout.canvasHeight, source)
        val canvas = Canvas(out)
        canvas.drawColor(borderColor)

        val fill = Paint().apply { color = lineColor; isAntiAlias = false; style = Paint.Style.FILL }
        for (ring in layout.rings) {
            for (bar in ring.bars()) {
                canvas.drawRect(bar.left.toFloat(), bar.top.toFloat(), bar.right.toFloat(), bar.bottom.toFloat(), fill)
            }
        }

        val upright = Orientation.matrix(exif, source.width, source.height)
        val srcW = if (Orientation.swapsAxes(exif)) source.height else source.width
        val srcH = if (Orientation.swapsAxes(exif)) source.width else source.height
        val dst = layout.photo
        val sx = dst.width.toFloat() / srcW
        val sy = dst.height.toFloat() / srcH
        upright.postScale(sx, sy)
        upright.postTranslate(dst.left.toFloat(), dst.top.toFloat())

        val scaled = sx != 1f || sy != 1f
        val paint = Paint().apply { isAntiAlias = false; isDither = false; isFilterBitmap = scaled }
        canvas.save()
        canvas.clipRect(Rect(dst.left, dst.top, dst.right, dst.bottom))
        canvas.drawBitmap(source, upright, paint)
        canvas.restore()
        return out
    }

    /** Keep the photo's colour space (e.g. Display P3) so wide-gamut photos don't shift. */
    private fun createCanvasBitmap(w: Int, h: Int, like: Bitmap): Bitmap {
        val cs = like.colorSpace
        if (cs != null) {
            try {
                return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
            } catch (_: IllegalArgumentException) {
                // fall through to sRGB
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.SRGB))
    }
}
