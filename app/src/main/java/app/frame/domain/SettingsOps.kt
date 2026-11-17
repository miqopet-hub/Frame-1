package app.frame.domain

/** The four border widths (in %) that the current style resolves to. */
fun BorderSettings.resolvedSides(): Sides = when (style) {
    BorderStyle.EVEN, BorderStyle.SQUARE, BorderStyle.DOUBLE -> Sides.uniform(base)
    BorderStyle.WEIGHTED -> Sides(base, base, base * bottomWeight, base)
    BorderStyle.ASYMMETRIC -> sides
}

/** Value shown by the global "all sides" slider. */
fun BorderSettings.globalValue(): Float =
    if (style == BorderStyle.ASYMMETRIC) sides.mean() else base

/** The global slider: scales every side proportionally. */
fun BorderSettings.withGlobal(value: Float): BorderSettings {
    val v = value.coerceIn(0f, Limits.MAX_PERCENT)
    return if (style == BorderStyle.ASYMMETRIC) copy(sides = sides.scaledToMean(v)) else copy(base = v)
}

/**
 * Switch style. Entering Asymmetric seeds the four sides from what is currently on
 * screen, so the preview does not jump, and starts unlinked so the sides are editable.
 * Leaving Asymmetric returns to the linked state.
 */
fun BorderSettings.withStyle(newStyle: BorderStyle): BorderSettings {
    if (newStyle == style) return this
    return when (newStyle) {
        BorderStyle.ASYMMETRIC -> copy(
            style = newStyle,
            sides = resolvedSides().coerceAtMost(Limits.MAX_PERCENT),
            linked = false,
        )
        else -> copy(style = newStyle, linked = true)
    }
}

/**
 * Link / unlink the sides. Unlinking from a non-Asymmetric style switches to
 * Asymmetric (seeded from the current look) because independent sides *are*
 * the Asymmetric style.
 */
fun BorderSettings.withLinked(linked: Boolean): BorderSettings {
    if (!linked && style != BorderStyle.ASYMMETRIC) return withStyle(BorderStyle.ASYMMETRIC)
    return copy(linked = linked)
}

fun BorderSettings.withSide(side: Side, value: Float): BorderSettings =
    copy(sides = sides.with(side, value.coerceIn(0f, Limits.MAX_PERCENT)))
