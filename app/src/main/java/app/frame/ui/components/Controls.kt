package app.frame.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.frame.domain.ColorMath
import app.frame.ui.theme.FramePalette
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Slider with a non-linear (power) response, so small border widths are easy to dial
 * in while the full range up to [max] stays reachable.
 *
 * @param power 1 = linear. 2 = quadratic (finer control near zero).
 */
@Composable
fun ValueSlider(
    label: String,
    value: Float,
    max: Float,
    valueText: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    power: Float = 2f,
) {
    val t = (value / max).coerceIn(0f, 1f).pow(1f / power)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Slider(
            value = t,
            onValueChange = { onChange(max * it.pow(power)) },
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = FramePalette.Outline,
            ),
        )
    }
}

/** "5.0% · 150 px" */
fun widthText(percent: Float, shorterSide: Int): String {
    val px = (percent / 100f * shorterSide).roundToInt()
    return "${"%.1f".format(percent)}%  ·  $px px"
}

@Composable
fun <T> SegmentedChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else FramePalette.SurfaceHigh)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Swatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(color))
            .border(
                BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else FramePalette.Outline),
                CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * HEX entry with live validation. A valid 6-digit value applies immediately; a 3-digit
 * shorthand applies when the user presses Done. The field follows external colour changes
 * (eyedropper, palette) without fighting the user's typing.
 */
@Composable
fun HexField(
    color: Int,
    onColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(ColorMath.toHex(color)) }

    LaunchedEffect(color) {
        val parsed = ColorMath.parseHex(text)
        if (parsed == null || (parsed and 0xFFFFFF) != (color and 0xFFFFFF)) text = ColorMath.toHex(color)
    }

    val digits = text.removePrefix("#")
    val valid = ColorMath.parseHex(text) != null
    val showError = digits.isNotEmpty() && !valid && digits.length >= 6

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
            val withHash = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
            text = withHash.take(7)
            val d = text.removePrefix("#")
            if (d.length == 6) ColorMath.parseHex(text)?.let(onColor)
        },
        singleLine = true,
        isError = showError,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { ColorMath.parseHex(text)?.let(onColor) },
        ),
        placeholder = { Text("#RRGGBB") },
        supportingText = { if (showError) Text("Use 6 hex digits, e.g. #F2E9DC") },
        modifier = modifier,
    )
}

@Composable
fun SectionHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp),
    )
}

@Composable
fun ColorChip(color: Int, size: androidx.compose.ui.unit.Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color))
            .border(1.dp, FramePalette.Outline, RoundedCornerShape(6.dp)),
    )
}
