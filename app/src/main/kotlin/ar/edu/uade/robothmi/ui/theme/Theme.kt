package ar.edu.uade.robothmi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de la HMI. Hay dos: la oscura, pensada para la ronda nocturna en una garita con poca
 * luz, y la clara, para usar de día o con mucha luz ambiente. Los estados nunca dependen solo
 * del color: siempre llevan ícono y texto.
 *
 * La paleta sale del contexto de uso: en la oscura, fondos oscuros y fríos para no encandilar al
 * operador; en las dos, colores cálidos y saturados (Warn/Error) reservados para lo que exige atención
 * inmediata, así resaltan cuando aparecen. El texto tiene contraste alto con el fondo para leerse bien.
 * Todos los colores están definidos acá: si hay que ajustar uno, se cambia en un solo lugar.
 */
data class HmiPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
    val info: Color,
)

val DarkPalette = HmiPalette(
    isDark = true,
    background = Color(0xFF0E1318),
    surface = Color(0xFF161D24),
    surfaceHigh = Color(0xFF1F2831),
    outline = Color(0xFF2E3A46),
    text = Color(0xFFE6EDF3),
    textMuted = Color(0xFF93A1AE),
    accent = Color(0xFF4FD1C5),
    ok = Color(0xFF5ED68A),
    warn = Color(0xFFF5B841),
    error = Color(0xFFFF6B5E),
    info = Color(0xFF6CB6FF),
)

/** Tonos más oscuros que en la paleta oscura para mantener contraste sobre fondo claro. */
val LightPalette = HmiPalette(
    isDark = false,
    background = Color(0xFFF3F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8EDF1),
    outline = Color(0xFFC8D1D9),
    text = Color(0xFF111A22),
    textMuted = Color(0xFF55636F),
    accent = Color(0xFF0B7A71),
    ok = Color(0xFF1B7F45),
    warn = Color(0xFF9A5F00),
    error = Color(0xFFC0352B),
    info = Color(0xFF1A65B5),
)

val LocalHmiPalette = staticCompositionLocalOf { DarkPalette }

/** Acceso corto a los colores de la paleta activa. */
object Hmi {
    val palette: HmiPalette @Composable @ReadOnlyComposable get() = LocalHmiPalette.current
    val isDark: Boolean @Composable @ReadOnlyComposable get() = palette.isDark
    val Background: Color @Composable @ReadOnlyComposable get() = palette.background
    val Surface: Color @Composable @ReadOnlyComposable get() = palette.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = palette.surfaceHigh
    val Outline: Color @Composable @ReadOnlyComposable get() = palette.outline
    val Text: Color @Composable @ReadOnlyComposable get() = palette.text
    val TextMuted: Color @Composable @ReadOnlyComposable get() = palette.textMuted
    val Accent: Color @Composable @ReadOnlyComposable get() = palette.accent
    val Ok: Color @Composable @ReadOnlyComposable get() = palette.ok
    val Warn: Color @Composable @ReadOnlyComposable get() = palette.warn
    val Error: Color @Composable @ReadOnlyComposable get() = palette.error
    val Info: Color @Composable @ReadOnlyComposable get() = palette.info
}

/**
 * Significado semántico de un color (OK, advertencia, error…). Las pantallas eligen un tono, no un hex.
 * El color se resuelve en el momento (getter @Composable) para que tome la paleta activa, clara u oscura.
 */
enum class Tone {
    OK, WARN, ERROR, INFO, ACCENT, MUTED;

    val color: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            OK -> Hmi.Ok
            WARN -> Hmi.Warn
            ERROR -> Hmi.Error
            INFO -> Hmi.Info
            ACCENT -> Hmi.Accent
            MUTED -> Hmi.TextMuted
        }
}

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color(0xFF06231F),
    primaryContainer = Color(0xFF12423D),
    onPrimaryContainer = Color(0xFFB8F1EA),
    secondary = DarkPalette.info,
    onSecondary = Color(0xFF06223D),
    secondaryContainer = Color(0xFF1B3550),
    onSecondaryContainer = Color(0xFFD3E7FF),
    background = DarkPalette.background,
    onBackground = DarkPalette.text,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.surfaceHigh,
    onSurfaceVariant = DarkPalette.textMuted,
    surfaceContainer = DarkPalette.surface,
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainerHigh = DarkPalette.surfaceHigh,
    surfaceContainerHighest = DarkPalette.surfaceHigh,
    outline = DarkPalette.outline,
    outlineVariant = DarkPalette.outline,
    error = DarkPalette.error,
    onError = Color(0xFF3B0A05),
    errorContainer = Color(0xFF4A1712),
    onErrorContainer = Color(0xFFFFD9D4),
)

private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EFEA),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = LightPalette.info,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E6FA),
    onSecondaryContainer = Color(0xFF0A2340),
    background = LightPalette.background,
    onBackground = LightPalette.text,
    surface = LightPalette.surface,
    onSurface = LightPalette.text,
    surfaceVariant = LightPalette.surfaceHigh,
    onSurfaceVariant = LightPalette.textMuted,
    surfaceContainer = LightPalette.surface,
    surfaceContainerLow = LightPalette.surface,
    surfaceContainerHigh = LightPalette.surfaceHigh,
    surfaceContainerHighest = LightPalette.surfaceHigh,
    outline = LightPalette.outline,
    outlineVariant = LightPalette.outline,
    error = LightPalette.error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun RobotHmiTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHmiPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
