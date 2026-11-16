package ar.edu.uade.robothmi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema único y oscuro: el servicio es una ronda nocturna y el operador mira la pantalla
 * en una garita con poca luz. Los estados nunca dependen solo del color: siempre llevan
 * ícono y texto.
 */
object Hmi {
    val Background = Color(0xFF0E1318)
    val Surface = Color(0xFF161D24)
    val SurfaceHigh = Color(0xFF1F2831)
    val Outline = Color(0xFF2E3A46)
    val Text = Color(0xFFE6EDF3)
    val TextMuted = Color(0xFF93A1AE)
    val Accent = Color(0xFF4FD1C5)
    val Ok = Color(0xFF5ED68A)
    val Warn = Color(0xFFF5B841)
    val Error = Color(0xFFFF6B5E)
    val Info = Color(0xFF6CB6FF)
}

enum class Tone(val color: Color) {
    OK(Hmi.Ok),
    WARN(Hmi.Warn),
    ERROR(Hmi.Error),
    INFO(Hmi.Info),
    ACCENT(Hmi.Accent),
    MUTED(Hmi.TextMuted),
}

private val Scheme = darkColorScheme(
    primary = Hmi.Accent,
    onPrimary = Color(0xFF06231F),
    primaryContainer = Color(0xFF12423D),
    onPrimaryContainer = Color(0xFFB8F1EA),
    secondary = Hmi.Info,
    onSecondary = Color(0xFF06223D),
    secondaryContainer = Color(0xFF1B3550),
    onSecondaryContainer = Color(0xFFD3E7FF),
    background = Hmi.Background,
    onBackground = Hmi.Text,
    surface = Hmi.Surface,
    onSurface = Hmi.Text,
    surfaceVariant = Hmi.SurfaceHigh,
    onSurfaceVariant = Hmi.TextMuted,
    surfaceContainer = Hmi.Surface,
    surfaceContainerLow = Hmi.Surface,
    surfaceContainerHigh = Hmi.SurfaceHigh,
    surfaceContainerHighest = Hmi.SurfaceHigh,
    outline = Hmi.Outline,
    outlineVariant = Hmi.Outline,
    error = Hmi.Error,
    onError = Color(0xFF3B0A05),
    errorContainer = Color(0xFF4A1712),
    onErrorContainer = Color(0xFFFFD9D4),
)

@Composable
fun RobotHmiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
