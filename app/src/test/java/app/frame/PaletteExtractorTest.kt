package app.frame

import app.frame.imaging.PaletteExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteExtractorTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** A synthetic "photo" made of [tones] horizontal bands, each with a little noise. */
    private fun bands(vararg tones: Triple<Int, Int, Int>, perBand: Int = 400): IntArray {
        val out = ArrayList<Int>()
        var seed = 12345
        for ((r, g, b) in tones) repeat(perBand) {
            seed = seed * 1103515245 + 12345
            val n = (seed ushr 16) % 7 - 3
            out += argb((r + n).coerceIn(0, 255), (g + n).coerceIn(0, 255), (b + n).coerceIn(0, 255))
        }
        return out.toIntArray()
    }

    @Test
    fun alwaysReturnsTenColours() {
        val photo = bands(
            Triple(10, 20, 200), Triple(220, 30, 30), Triple(30, 200, 40), Triple(240, 240, 20),
            Triple(120, 60, 160), Triple(255, 255, 255), Triple(0, 0, 0), Triple(130, 130, 130),
            Triple(250, 140, 20), Triple(20, 200, 200), Triple(90, 50, 20), Triple(255, 150, 190),
        )
        assertEquals(10, PaletteExtractor.extract(photo, 10).size)
    }

    @Test
    fun isDeterministic() {
        val photo = bands(Triple(10, 20, 200), Triple(220, 30, 30), Triple(30, 200, 40), Triple(5, 5, 5))
        assertEquals(PaletteExtractor.extract(photo, 10), PaletteExtractor.extract(photo, 10))
    }

    @Test
    fun differentPhotosGiveDifferentPalettes() {
        val warm = bands(Triple(220, 120, 60), Triple(200, 80, 40), Triple(240, 200, 150), Triple(120, 50, 30))
        val cool = bands(Triple(20, 60, 200), Triple(40, 120, 220), Triple(150, 200, 240), Triple(10, 30, 90))
        assertNotEquals(PaletteExtractor.extract(warm, 10), PaletteExtractor.extract(cool, 10))
    }

    @Test
    fun dominantColoursAreRepresented() {
        val photo = bands(Triple(200, 30, 30), Triple(30, 30, 200), perBand = 2000)
        val p = PaletteExtractor.extract(photo, 10)
        fun near(target: Int) = p.any { c ->
            val dr = ((c shr 16) and 0xFF) - ((target shr 16) and 0xFF)
            val dg = ((c shr 8) and 0xFF) - ((target shr 8) and 0xFF)
            val db = (c and 0xFF) - (target and 0xFF)
            dr * dr + dg * dg + db * db < 30 * 30
        }
        assertTrue("red missing", near(argb(200, 30, 30)))
        assertTrue("blue missing", near(argb(30, 30, 200)))
    }

    @Test
    fun flatImageStillYieldsTenDerivedSwatches() {
        val flat = IntArray(5000) { argb(120, 100, 80) }
        val p = PaletteExtractor.extract(flat, 10)
        assertEquals(10, p.size)
        assertEquals(10, p.distinct().size)
    }

    @Test
    fun allOutputColoursAreOpaque() {
        val photo = bands(Triple(10, 20, 200), Triple(220, 30, 30))
        assertTrue(PaletteExtractor.extract(photo, 10).all { (it ushr 24) == 0xFF })
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val px = IntArray(1000) { 0x00FF0000 } + IntArray(1000) { argb(0, 0, 255) }
        val p = PaletteExtractor.extract(px, 10)
        assertTrue(p.first() and 0xFF > 200)
    }
}
