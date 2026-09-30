package uz.jahonov.ktormonitor.ui.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The monitor's own colours and text styles; it follows the system's light or dark setting. */
@Composable
internal fun MonitorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (darkTheme) DarkPalette else LightPalette, content = content)
}

internal object MonitorTheme {
    val colors: MonitorPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val typography: MonitorTypography = MonitorTypography
}

internal class MonitorPalette(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val success: Color,
    val media: Color,
)

internal object MonitorTypography {
    val caption = style(12, 16, FontWeight.Normal)
    val captionMedium = style(12, 16, FontWeight.Medium)
    val captionBold = style(12, 16, FontWeight.Bold)
    val body = style(14, 20, FontWeight.Normal)
    val bodyMedium = style(14, 20, FontWeight.Medium)
    val bodyBold = style(14, 20, FontWeight.Bold)
    val subtitle = style(16, 24, FontWeight.Medium)
    val title = style(16, 24, FontWeight.Bold)
    val headline = style(18, 26, FontWeight.Bold)

    private fun style(size: Int, lineHeight: Int, weight: FontWeight) =
        TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)
}

private val LightPalette = MonitorPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F4F6),
    border = Color(0xFFE0E2E7),
    text = Color(0xFF16181D),
    textSecondary = Color(0xFF6B7080),
    textDisabled = Color(0xFFA6AAB5),
    accent = Color(0xFF2F6FEB),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE4EDFD),
    error = Color(0xFFD93A3A),
    errorContainer = Color(0xFFFDECEC),
    warning = Color(0xFFC77A00),
    warningContainer = Color(0xFFFFF4DE),
    info = Color(0xFF1A8CB0),
    success = Color(0xFF2E9D57),
    media = Color(0xFF8A4FD8),
)

private val DarkPalette = MonitorPalette(
    background = Color(0xFF121317),
    surface = Color(0xFF1C1E24),
    border = Color(0xFF2C2F37),
    text = Color(0xFFECEEF2),
    textSecondary = Color(0xFF9CA1AE),
    textDisabled = Color(0xFF5E6370),
    accent = Color(0xFF6C9CFF),
    onAccent = Color(0xFF0B1020),
    accentContainer = Color(0xFF1F2B45),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3A1E20),
    warning = Color(0xFFF2B24C),
    warningContainer = Color(0xFF3A2E17),
    info = Color(0xFF56C1E0),
    success = Color(0xFF5CCB84),
    media = Color(0xFFB58CFF),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }
