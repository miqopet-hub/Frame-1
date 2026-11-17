package app.frame.imaging

import kotlin.math.sqrt

/**
 * Deterministic median-cut palette extraction over ARGB pixels.
 *
 * No randomness and no fixed colours: the same photograph always yields the same
 * ten swatches, and a different photograph yields different ones.
 */
object PaletteExtractor {

    private class Bin(
        val r5: Int, val g5: Int, val b5: Int,
        var count: Int = 0,
        var sumR: Long = 0, var sumG: Long = 0, var sumB: Long = 0,
    )

    private class Box(val bins: List<Bin>) {
        val population: Long = bins.sumOf { it.count.toLong() }
        private val minC = IntArray(3) { Int.MAX_VALUE }
        private val maxC = IntArray(3) { Int.MIN_VALUE }

        init {
            for (b in bins) {
                val v = intArrayOf(b.r5, b.g5, b.b5)
                for (i in 0..2) {
                    if (v[i] < minC[i]) minC[i] = v[i]
                    if (v[i] > maxC[i]) maxC[i] = v[i]
                }
            }
        }

        fun longestAxis(): Int {
            var axis = 0
            var best = -1
            for (i in 0..2) {
                val range = maxC[i] - minC[i]
                if (range > best) { best = range; axis = i }
            }
            return axis
        }

        fun longestRange(): Int = maxC[longestAxis()] - minC[longestAxis()]
        fun splittable(): Boolean = bins.size > 1 && longestRange() > 0

        /** Score used to choose which box to split next. */
        fun score(): Double = (longestRange() + 1) * sqrt(population.toDouble())

        fun average(): Int {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (bin in bins) { r += bin.sumR; g += bin.sumG; b += bin.sumB; n += bin.count }
            if (n == 0L) return 0xFF000000.toInt()
            return pack((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }
    }

    private fun pack(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /**
     * @param pixels ARGB pixels (typically from a downscaled copy of the photo)
     * @return [count] opaque ARGB colours, most-represented first.
     */
    fun extract(pixels: IntArray, count: Int = 10): List<Int> {
        // 1. Histogram on a 5-bit-per-channel grid, keeping exact channel sums per bin.
        val bins = HashMap<Int, Bin>()
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val bin = bins.getOrPut(key) { Bin(r shr 3, g shr 3, b shr 3) }
            bin.count++
            bin.sumR += r; bin.sumG += g; bin.sumB += b
        }
        if (bins.isEmpty()) return emptyList()

        // 2. Median cut. Sorting keys are tie-broken so the result is fully deterministic.
        val boxes = mutableListOf(Box(bins.values.sortedBy { (it.r5 shl 10) or (it.g5 shl 5) or it.b5 }))
        while (boxes.size < count) {
            val target = boxes.filter { it.splittable() }.maxByOrNull { it.score() } ?: break
            boxes.remove(target)
            val axis = target.longestAxis()
            val sorted = target.bins.sortedWith(
                compareBy<Bin>(
                    { when (axis) { 0 -> it.r5; 1 -> it.g5; else -> it.b5 } },
                    { (it.r5 shl 10) or (it.g5 shl 5) or it.b5 },
                ),
            )
            val half = target.population / 2
            var acc = 0L
            var cut = 0
            for ((i, b) in sorted.withIndex()) {
                acc += b.count
                cut = i + 1
                if (acc >= half) break
            }
            cut = cut.coerceIn(1, sorted.size - 1)
            boxes += Box(sorted.subList(0, cut))
            boxes += Box(sorted.subList(cut, sorted.size))
        }

        val ordered = boxes.sortedByDescending { it.population }.map { it.average() }.distinct().toMutableList()

        // 3. Very flat photographs may hold fewer than [count] distinct tones. Fill the
        //    remainder with lighter/darker variants of colours that ARE in the photograph.
        var step = 1
        while (ordered.size < count && step <= 20) {
            val base = ordered.take(count).toList()
            for (c in base) {
                if (ordered.size >= count) break
                val amount = 0.12f * ((step + 1) / 2)
                val variant = if (step % 2 == 1) mix(c, 0xFFFFFFFF.toInt(), amount) else mix(c, 0xFF000000.toInt(), amount)
                if (variant !in ordered) ordered += variant
            }
            step++
        }
        return ordered.take(count)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt()
        }
        return pack(ch(16), ch(8), ch(0))
    }
}
