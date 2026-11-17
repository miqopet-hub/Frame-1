package app.frame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FramePalette {
    val Background = Color(0xFF0C0C0D)
    val Surface = Color(0xFF161617)
    val SurfaceHigh = Color(0xFF222224)
    val Outline = Color(0xFF3A3A3D)
    val OnSurface = Color(0xFFE8E8EA)
    val OnSurfaceDim = Color(0xFF9A9AA0)
    val Accent = Color(0xFFE8E8EA)
    val Error = Color(0xFFFF7A70)
}

private val Scheme = darkColorScheme(
    primary = FramePalette.Accent,
    onPrimary = Color(0xFF111112),
    secondary = FramePalette.SurfaceHigh,
    onSecondary = FramePalette.OnSurface,
    background = FramePalette.Background,
    onBackground = FramePalette.OnSurface,
    surface = FramePalette.Surface,
    onSurface = FramePalette.OnSurface,
    surfaceVariant = FramePalette.SurfaceHigh,
    onSurfaceVariant = FramePalette.OnSurfaceDim,
    outline = FramePalette.Outline,
    error = FramePalette.Error,
)

private val FrameTypography = Typography(
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
)

/** FRAME is always dark so the photograph is the only bright thing on screen. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun FrameTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = FrameTypography, content = content)
}
