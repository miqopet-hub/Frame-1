package app.frame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.frame.domain.FrameLayout
import app.frame.imaging.LoadedImage
import app.frame.ui.EyedropperSample
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws exactly what the exporter will produce, using the SAME [FrameLayout]:
 * canvas aspect ratio, border widths, line rectangles and photo placement are all
 * derived from it, just multiplied by one uniform scale factor.
 */
@Composable
fun FramePreview(
    image: LoadedImage,
    layout: FrameLayout,
    borderColor: Int,
    lineColor: Int,
    eyedropperActive: Boolean,
    sample: EyedropperSample?,
    onSample: (u: Float, v: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(image.preview) { image.preview.asImageBitmap() }
    val onSampleState = rememberUpdatedState(onSample)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()
        val fit = min(availW / layout.canvasWidth, availH / layout.canvasHeight)
        val widthPx = layout.canvasWidth * fit
        val heightPx = layout.canvasHeight * fit
        val widthDp = with(density) { widthPx.toDp() }
        val heightDp = with(density) { heightPx.toDp() }

        Canvas(
            modifier = Modifier
                .size(widthDp, heightDp)
                .pointerInput(eyedropperActive, layout) {
                    if (!eyedropperActive) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed) {
                                // Canvas pixels -> original-photo pixels -> normalised (u, v).
                                val s = size.width / layout.canvasWidth.toFloat()
                                val x = change.position.x / s
                                val y = change.position.y / s
                                val p = layout.photo
                                if (x >= p.left && x < p.right && y >= p.top && y < p.bottom) {
                                    onSampleState.value((x - p.left) / p.width, (y - p.top) / p.height)
                                }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val s = size.width / layout.canvasWidth.toFloat()

            drawRect(Color(borderColor))

            val line = Color(lineColor)
            for (ring in layout.rings) {
                for (bar in ring.bars()) {
                    drawRect(
                        color = line,
                        topLeft = Offset(bar.left * s, bar.top * s),
                        size = Size(bar.width * s, bar.height * s),
                    )
                }
            }

            val p = layout.photo
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset((p.left * s).roundToInt(), (p.top * s).roundToInt()),
                dstSize = IntSize((p.width * s).roundToInt(), (p.height * s).roundToInt()),
                filterQuality = FilterQuality.High,
            )

            if (eyedropperActive && sample != null) {
                val cx = (p.left + sample.u * p.width) * s
                val cy = (p.top + sample.v * p.height) * s
                val c = Offset(cx, cy)
                drawCircle(Color.Black, radius = 13f, center = c, style = Stroke(width = 5f))
                drawCircle(Color.White, radius = 13f, center = c, style = Stroke(width = 2.5f))
                drawCircle(Color(sample.color), radius = 6f, center = c)
            }
        }
    }
}
