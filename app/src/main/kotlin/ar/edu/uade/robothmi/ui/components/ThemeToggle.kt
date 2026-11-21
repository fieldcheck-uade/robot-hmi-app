package ar.edu.uade.robothmi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import ar.edu.uade.robothmi.model.ThemeMode
import ar.edu.uade.robothmi.ui.theme.Hmi

/**
 * Botón sol/luna. Muestra el tema al que se pasa: sol con el tema oscuro, luna con el claro.
 * Al tocarlo la elección queda guardada y deja de seguir la configuración del dispositivo.
 */
@Composable
fun ThemeToggleButton(onSelect: (ThemeMode) -> Unit) {
    val dark = Hmi.isDark
    IconButton(onClick = { onSelect(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }) {
        Icon(
            if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (dark) "Cambiar a tema claro" else "Cambiar a tema oscuro",
            tint = Hmi.TextMuted,
        )
    }
}
