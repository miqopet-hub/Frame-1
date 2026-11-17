package app.frame.imaging

import android.graphics.Matrix
import android.graphics.RectF

/**
 * EXIF orientation handling. The matrix maps a RAW (unrotated) bitmap into upright
 * space with its top-left at (0,0). It contains only 90-degree rotations and flips,
 * so applying it never resamples pixels.
 */
object Orientation {

    fun swapsAxes(exif: Int): Boolean = exif in 5..8

    fun matrix(exif: Int, rawW: Int, rawH: Int): Matrix {
        val m = Matrix()
        when (exif) {
            2 -> m.setScale(-1f, 1f)                              // mirror
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }      // flip vertical
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }       // transpose
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }      // transverse
            8 -> m.setRotate(-90f)
        }
        val r = RectF(0f, 0f, rawW.toFloat(), rawH.toFloat())
        m.mapRect(r)
        m.postTranslate(-r.left, -r.top)
        return m
    }
}
