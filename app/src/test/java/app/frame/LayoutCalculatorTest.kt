package app.frame

import app.frame.domain.BorderSettings
import app.frame.domain.BorderStyle
import app.frame.domain.ColorMath
import app.frame.domain.DoubleLineSettings
import app.frame.domain.LayoutCalculator
import app.frame.domain.Limits
import app.frame.domain.Side
import app.frame.domain.Sides
import app.frame.domain.globalValue
import app.frame.domain.resolvedSides
import app.frame.domain.withGlobal
import app.frame.domain.withLinked
import app.frame.domain.withSide
import app.frame.domain.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCalculatorTest {

    private val sizes = listOf(4000 to 3000, 3000 to 4000, 3000 to 3000, 1 to 1, 5000 to 100, 123 to 4567)

    private fun settingsFor(style: BorderStyle) = BorderSettings(style = style, base = 7.3f, sides = Sides(3f, 9f, 14f, 1f))

    @Test
    fun photoIsNeverResizedByAnyStyle() {
        for ((w, h) in sizes) for (style in BorderStyle.entries) {
            val l = LayoutCalculator.compute(w, h, settingsFor(style))
            assertEquals("$style ${w}x$h width", w, l.photo.width)
            assertEquals("$style ${w}x$h height", h, l.photo.height)
        }
    }

    @Test
    fun canvasIsExactlyPhotoPlusBorders() {
        for ((w, h) in sizes) for (style in BorderStyle.entries) {
            val l = LayoutCalculator.compute(w, h, settingsFor(style))
            assertEquals(l.sides.left + w + l.sides.right, l.canvasWidth)
            assertEquals(l.sides.top + h + l.sides.bottom, l.canvasHeight)
            assertEquals(l.sides.left, l.photo.left)
            assertEquals(l.sides.top, l.photo.top)
        }
    }

    @Test
    fun evenHasEqualSides() {
        val l = LayoutCalculator.compute(4000, 3000, BorderSettings(style = BorderStyle.EVEN, base = 5f))
        // 5% of the shorter side (3000) = 150
        assertEquals(150, l.sides.top); assertEquals(150, l.sides.right)
        assertEquals(150, l.sides.bottom); assertEquals(150, l.sides.left)
        assertEquals(4300, l.canvasWidth); assertEquals(3300, l.canvasHeight)
    }

    @Test
    fun weightedBottomIsLargerThanOtherSides() {
        val l = LayoutCalculator.compute(4000, 3000, BorderSettings(style = BorderStyle.WEIGHTED, base = 4f, bottomWeight = 2.5f))
        assertEquals(120, l.sides.top)
        assertEquals(120, l.sides.left)
        assertEquals(120, l.sides.right)
        assertEquals(300, l.sides.bottom)
    }

    @Test
    fun asymmetricUsesIndependentSides() {
        val s = BorderSettings(style = BorderStyle.ASYMMETRIC, sides = Sides(top = 1f, right = 2f, bottom = 3f, left = 4f))
        val l = LayoutCalculator.compute(1000, 1000, s)
        assertEquals(10, l.sides.top); assertEquals(20, l.sides.right)
        assertEquals(30, l.sides.bottom); assertEquals(40, l.sides.left)
    }

    @Test
    fun squareCanvasIsSquareAndPhotoIsCentred() {
        for ((w, h) in sizes) for (base in listOf(0f, 3f, 10f, 40f)) {
            val l = LayoutCalculator.compute(w, h, BorderSettings(style = BorderStyle.SQUARE, base = base))
            assertEquals("square for ${w}x$h base=$base", l.canvasWidth, l.canvasHeight)
            assertEquals(w, l.photo.width); assertEquals(h, l.photo.height)
            // centred to within 1px (an odd leftover pixel can't be split evenly)
            assertTrue(Math.abs(l.sides.left - l.sides.right) <= 1)
            assertTrue(Math.abs(l.sides.top - l.sides.bottom) <= 1)
        }
    }

    @Test
    fun squareWithZeroBorderOnlyPadsTheShortDimension() {
        val l = LayoutCalculator.compute(4000, 3000, BorderSettings(style = BorderStyle.SQUARE, base = 0f))
        assertEquals(4000, l.canvasWidth); assertEquals(4000, l.canvasHeight)
        assertEquals(0, l.sides.left); assertEquals(0, l.sides.right)
        assertEquals(500, l.sides.top); assertEquals(500, l.sides.bottom)
    }

    @Test
    fun doubleLinesStayInsideTheBorderBand() {
        for ((w, h) in sizes) for (pos in listOf(0f, 0.5f, 1f)) {
            val s = BorderSettings(
                style = BorderStyle.DOUBLE, base = 6f,
                doubleLines = DoubleLineSettings(thickness = 0.4f, gap = 0.8f, position = pos),
            )
            val l = LayoutCalculator.compute(w, h, s)
            for (ring in l.rings) {
                val o = ring.outer
                assertTrue(o.left >= 0 && o.top >= 0 && o.right <= l.canvasWidth && o.bottom <= l.canvasHeight)
                // Never touches the photo:
                assertTrue("ring overlaps photo", o.left + ring.thickness <= l.photo.left)
                assertTrue(o.top + ring.thickness <= l.photo.top)
                assertTrue(o.right - ring.thickness >= l.photo.right)
                assertTrue(o.bottom - ring.thickness >= l.photo.bottom)
            }
        }
    }

    @Test
    fun doubleProducesTwoRingsWhenThereIsRoom() {
        val l = LayoutCalculator.compute(4000, 3000, BorderSettings(style = BorderStyle.DOUBLE, base = 5f))
        assertEquals(2, l.rings.size)
        assertEquals(8, l.rings.sumOf { it.bars().size })
    }

    @Test
    fun scaledLayoutKeepsAspectRatioWithinRounding() {
        val l = LayoutCalculator.compute(8000, 6000, BorderSettings(style = BorderStyle.WEIGHTED, base = 5f))
        val s = l.scaled(0.5f)
        val a = l.canvasWidth.toDouble() / l.canvasHeight
        val b = s.canvasWidth.toDouble() / s.canvasHeight
        assertEquals(a, b, 0.001)
        assertEquals(l.photo.width / 2, s.photo.width)
    }

    @Test
    fun exportScaleOnlyAppliesAboveTheCap() {
        val small = LayoutCalculator.compute(4000, 3000, BorderSettings())
        assertEquals(1f, LayoutCalculator.exportScaleFor(small), 0f)
        val huge = LayoutCalculator.compute(20000, 15000, BorderSettings())
        val scale = LayoutCalculator.exportScaleFor(huge)
        assertTrue(scale < 1f)
        val px = huge.scaled(scale).let { it.canvasWidth.toLong() * it.canvasHeight }
        assertTrue(px <= LayoutCalculator.MAX_EXPORT_PIXELS * 1.001)
    }

    // ---- settings behaviour ------------------------------------------------------------

    @Test
    fun globalSliderScalesAsymmetricSidesProportionally() {
        val s = BorderSettings(style = BorderStyle.ASYMMETRIC, sides = Sides(2f, 4f, 8f, 2f), linked = true)
        val doubled = s.withGlobal(s.globalValue() * 2)
        assertEquals(4f, doubled.sides.top, 1e-4f)
        assertEquals(8f, doubled.sides.right, 1e-4f)
        assertEquals(16f, doubled.sides.bottom, 1e-4f)
        assertEquals(4f, doubled.sides.left, 1e-4f)
    }

    @Test
    fun proportionalScalingNeverExceedsTheMaximumOrBreaksRatios() {
        val s = BorderSettings(style = BorderStyle.ASYMMETRIC, sides = Sides(10f, 20f, 40f, 10f))
        val big = s.withGlobal(100f)
        assertTrue(big.sides.max() <= Limits.MAX_PERCENT + 1e-3f)
        assertEquals(big.sides.bottom / big.sides.top, 4f, 1e-3f)
    }

    @Test
    fun enteringAsymmetricKeepsTheCurrentLook() {
        val weighted = BorderSettings(style = BorderStyle.WEIGHTED, base = 4f, bottomWeight = 2f)
        val asym = weighted.withStyle(BorderStyle.ASYMMETRIC)
        assertEquals(weighted.resolvedSides(), asym.resolvedSides())
        assertEquals(false, asym.linked)
    }

    @Test
    fun unlinkingFromEvenSwitchesToAsymmetricWithoutChangingThePreview() {
        val even = BorderSettings(style = BorderStyle.EVEN, base = 6f)
        val unlinked = even.withLinked(false)
        assertEquals(BorderStyle.ASYMMETRIC, unlinked.style)
        assertEquals(even.resolvedSides(), unlinked.resolvedSides())
        val edited = unlinked.withSide(Side.BOTTOM, 20f)
        assertEquals(20f, edited.sides.bottom, 0f)
        assertEquals(6f, edited.sides.top, 0f)
    }

    // ---- colour helpers ------------------------------------------------------------------

    @Test
    fun hexParsing() {
        assertEquals(0xFFF2E9DC.toInt(), ColorMath.parseHex("#F2E9DC"))
        assertEquals(0xFFF2E9DC.toInt(), ColorMath.parseHex("f2e9dc"))
        assertEquals(0xFFAABBCC.toInt(), ColorMath.parseHex("#abc"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex(""))
        assertNotNull(ColorMath.parseHex(" #000000 "))
        assertEquals("#F2E9DC", ColorMath.toHex(0xFFF2E9DC.toInt()))
    }
}
