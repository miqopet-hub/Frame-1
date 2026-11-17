package app.frame.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Integer pixel rectangle, [left, right) x [top, bottom). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * One rectangular line, drawn as a ring of [thickness] px *inside* [outer].
 * [bars] gives the four filled rectangles that make it up, so the Compose preview
 * and the Canvas export draw literally the same geometry.
 */
data class RingSpec(val outer: PxRect, val thickness: Int) {
    fun bars(): List<PxRect> {
        val o = outer
        val t = thickness
        if (t <= 0 || o.width <= 2 * t || o.height <= 2 * t) return emptyList()
        return listOf(
            PxRect(o.left, o.top, o.right, o.top + t),
            PxRect(o.left, o.bottom - t, o.right, o.bottom),
            PxRect(o.left, o.top + t, o.left + t, o.bottom - t),
            PxRect(o.right - t, o.top + t, o.right, o.bottom - t),
        )
    }
}

data class PxSides(val top: Int, val right: Int, val bottom: Int, val left: Int)

/**
 * The single source of truth for geometry. The preview scales this by a constant
 * factor; the exporter renders it at scale 1 (or a uniform reduction when memory
 * demands it). The photograph is only ever *placed* in [photo] - never altered.
 */
data class FrameLayout(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val photo: PxRect,
    val rings: List<RingSpec>,
    val sides: PxSides,
) {
    val megapixels: Double get() = canvasWidth.toDouble() * canvasHeight / 1_000_000.0

    /** Uniformly scale the whole composition (used only when export must be reduced). */
    fun scaled(s: Float): FrameLayout {
        if (s == 1f) return this
        fun r(v: Int) = (v * s).roundToInt()
        fun rect(p: PxRect) = PxRect(r(p.left), r(p.top), r(p.right), r(p.bottom))
        val cw = max(1, r(canvasWidth))
        val ch = max(1, r(canvasHeight))
        return FrameLayout(
            canvasWidth = cw,
            canvasHeight = ch,
            photo = rect(photo),
            rings = rings.map { RingSpec(rect(it.outer), max(1, r(it.thickness))) },
            sides = PxSides(r(sides.top), r(sides.right), r(sides.bottom), r(sides.left)),
        )
    }
}

object LayoutCalculator {

    /** Hard ceiling on exported canvas size, to stay clear of out-of-memory crashes. */
    const val MAX_EXPORT_PIXELS = 100_000_000L

    /**
     * @param photoW,photoH dimensions of the photograph AFTER EXIF orientation is applied.
     */
    fun compute(photoW: Int, photoH: Int, settings: BorderSettings): FrameLayout {
        require(photoW > 0 && photoH > 0) { "Photo must have positive size" }
        val shorter = min(photoW, photoH)
        fun px(percent: Float) = (percent / 100f * shorter).roundToInt().coerceAtLeast(0)

        val sides: PxSides = when (settings.style) {
            BorderStyle.EVEN -> px(settings.base).let { PxSides(it, it, it, it) }
            BorderStyle.WEIGHTED -> px(settings.base).let { b ->
                PxSides(b, b, px(settings.base * settings.bottomWeight), b)
            }
            BorderStyle.ASYMMETRIC -> settings.sides.let {
                PxSides(px(it.top), px(it.right), px(it.bottom), px(it.left))
            }
            BorderStyle.DOUBLE -> px(settings.base).let { PxSides(it, it, it, it) }
            BorderStyle.SQUARE -> {
                // Even border first, then pad the SHORTER dimension of the result so the
                // canvas becomes square. The photo stays centred and untouched.
                val e = px(settings.base)
                val w = photoW + 2 * e
                val h = photoH + 2 * e
                val side = max(w, h)
                val extraX = side - w
                val extraY = side - h
                PxSides(
                    top = e + extraY / 2,
                    bottom = e + extraY - extraY / 2,
                    left = e + extraX / 2,
                    right = e + extraX - extraX / 2,
                )
            }
        }

        val canvasW = sides.left + photoW + sides.right
        val canvasH = sides.top + photoH + sides.bottom
        val photo = PxRect(sides.left, sides.top, sides.left + photoW, sides.top + photoH)

        val rings = if (settings.style == BorderStyle.DOUBLE) {
            doubleRings(canvasW, canvasH, sides.top, shorter, settings.doubleLines)
        } else emptyList()

        return FrameLayout(canvasW, canvasH, photo, rings, sides)
    }

    private fun doubleRings(
        canvasW: Int,
        canvasH: Int,
        band: Int,
        shorter: Int,
        d: DoubleLineSettings,
    ): List<RingSpec> {
        if (band < 3) return emptyList()
        fun px(percent: Float) = (percent / 100f * shorter).roundToInt()
        var t = max(1, px(d.thickness))
        var g = max(1, px(d.gap))
        var span = 2 * t + g
        if (span > band) {
            // Shrink the line pair proportionally so it always fits inside the border.
            val f = band.toFloat() / span
            t = max(1, (t * f).toInt())
            g = max(1, (g * f).toInt())
            span = 2 * t + g
            if (span > band) return emptyList()
        }
        val offset = (d.position.coerceIn(0f, 1f) * (band - span)).roundToInt()
        val outer = PxRect(offset, offset, canvasW - offset, canvasH - offset)
        val inset = t + g
        val inner = PxRect(outer.left + inset, outer.top + inset, outer.right - inset, outer.bottom - inset)
        return listOf(RingSpec(outer, t), RingSpec(inner, t))
    }

    /** Uniform scale (<= 1) needed so the canvas fits within [MAX_EXPORT_PIXELS]. */
    fun exportScaleFor(layout: FrameLayout): Float {
        val px = layout.canvasWidth.toDouble() * layout.canvasHeight
        return if (px <= MAX_EXPORT_PIXELS) 1f else sqrt(MAX_EXPORT_PIXELS / px).toFloat()
    }
}

/** Colour helpers kept here (pure) so preview and export agree on the line colour. */
object ColorMath {
    /** Relative luminance 0..1 of an ARGB int. */
    fun luminance(argb: Int): Float {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)).toFloat()
    }

    fun resolveLineColor(mode: LineColorMode, border: Int): Int = when (mode) {
        LineColorMode.BLACK -> 0xFF111111.toInt()
        LineColorMode.WHITE -> 0xFFF5F5F5.toInt()
        LineColorMode.AUTO -> if (luminance(border) > 0.4f) 0xFF111111.toInt() else 0xFFF5F5F5.toInt()
    }

    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Accepts "#RRGGBB", "RRGGBB", "#RGB", "RGB" (case-insensitive). Returns opaque ARGB or null. */
    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        val full = when (t.length) {
            6 -> t
            3 -> t.map { "$it$it" }.joinToString("")
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF000000L or full.toLong(16)).toInt()
    }
}
