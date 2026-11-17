package app.frame.domain

/**
 * Pure, Android-free models. Everything here is unit-testable on the JVM.
 *
 * All border sizes are expressed as a PERCENTAGE OF THE PHOTO'S SHORTER SIDE, so a
 * given setting looks the same on a 12 MP and a 50 MP photograph.
 */
enum class BorderStyle(val label: String) {
    EVEN("Even"),
    WEIGHTED("Weighted"),
    ASYMMETRIC("Asymmetric"),
    SQUARE("Square"),
    DOUBLE("Double"),
}

enum class LineColorMode(val label: String) {
    AUTO("Auto"),
    BLACK("Black"),
    WHITE("White"),
}

data class Sides(val top: Float, val right: Float, val bottom: Float, val left: Float) {
    fun mean(): Float = (top + right + bottom + left) / 4f
    fun max(): Float = maxOf(maxOf(top, right), maxOf(bottom, left))
    fun coerceAtMost(limit: Float) = Sides(
        top.coerceAtMost(limit), right.coerceAtMost(limit),
        bottom.coerceAtMost(limit), left.coerceAtMost(limit),
    )

    /**
     * Scale all four sides by the same factor so that their mean becomes [newMean].
     * Ratios between the sides are preserved. No side exceeds [Limits.MAX_PERCENT];
     * if that would be violated the factor is reduced so proportions stay intact.
     */
    fun scaledToMean(newMean: Float): Sides {
        val target = newMean.coerceIn(0f, Limits.MAX_PERCENT)
        val current = mean()
        if (current <= 0.0001f) return uniform(target)
        var factor = target / current
        val biggest = max() * factor
        if (biggest > Limits.MAX_PERCENT) factor = Limits.MAX_PERCENT / max()
        return Sides(top * factor, right * factor, bottom * factor, left * factor)
    }

    companion object {
        fun uniform(v: Float) = Sides(v, v, v, v)
    }
}

enum class Side { TOP, RIGHT, BOTTOM, LEFT }

fun Sides.with(side: Side, value: Float): Sides = when (side) {
    Side.TOP -> copy(top = value)
    Side.RIGHT -> copy(right = value)
    Side.BOTTOM -> copy(bottom = value)
    Side.LEFT -> copy(left = value)
}

fun Sides.get(side: Side): Float = when (side) {
    Side.TOP -> top
    Side.RIGHT -> right
    Side.BOTTOM -> bottom
    Side.LEFT -> left
}

object Limits {
    /** Upper bound of a single border, in % of the photo's shorter side. */
    const val MAX_PERCENT = 100f
    const val MAX_LINE_THICKNESS = 3f
    const val MAX_LINE_GAP = 6f
    const val MIN_WEIGHT = 1f
    const val MAX_WEIGHT = 6f
}

data class DoubleLineSettings(
    /** Thickness of each of the two lines, % of shorter side. */
    val thickness: Float = 0.25f,
    /** Gap between the two lines, % of shorter side. */
    val gap: Float = 0.5f,
    /** 0 = lines hug the canvas edge, 1 = lines hug the photo, 0.5 = centred in the border. */
    val position: Float = 0.5f,
    val colorMode: LineColorMode = LineColorMode.AUTO,
)

data class BorderSettings(
    val style: BorderStyle = BorderStyle.EVEN,
    /** Border width for Even / Weighted / Square / Double (% of shorter side). */
    val base: Float = 5f,
    /** Per-side widths, used by Asymmetric. */
    val sides: Sides = Sides.uniform(5f),
    /** Asymmetric only: true = global slider scales all sides proportionally. */
    val linked: Boolean = true,
    /** Weighted: bottom border = base * bottomWeight. */
    val bottomWeight: Float = 2.5f,
    val doubleLines: DoubleLineSettings = DoubleLineSettings(),
)
