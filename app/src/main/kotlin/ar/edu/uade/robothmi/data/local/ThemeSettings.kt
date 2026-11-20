package ar.edu.uade.robothmi.data.local

import android.content.Context
import ar.edu.uade.robothmi.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tema elegido por el usuario, guardado en SharedPreferences. Es un único valor, así que no
 * justifica una tabla de Room. Mientras el usuario no elija, vale SYSTEM.
 */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs.getString(KEY, null)?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM,
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
