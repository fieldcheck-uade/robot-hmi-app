package ar.edu.uade.robothmi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.robothmi.ui.AppRoot
import ar.edu.uade.robothmi.ui.RobotViewModel
import ar.edu.uade.robothmi.ui.theme.RobotHmiTheme

/**
 * Única Activity de la app: es el punto de entrada que abre Android al tocar el ícono
 * (está declarada en el Manifest con el intent-filter MAIN/LAUNCHER).
 *
 * Usamos una sola Activity (Single Activity) y todas las pantallas son composables dentro de ella.
 * En nuestra app eso simplifica mucho el ciclo de vida: hay un solo onCreate/onStop/onDestroy que
 * cuidar, en vez de uno por pantalla, y no aparece el doble ciclo de vida de los Fragments.
 */
class MainActivity : ComponentActivity() {
    // Obtenemos el ViewModel con `by viewModels` en lugar de crearlo con `RobotViewModel(...)`.
    // Así Android lo guarda en el ViewModelStore de la Activity: cuando el operador gira el teléfono,
    // la Activity se destruye y se recrea, pero se reconecta al MISMO ViewModel. Es importante porque
    // si no, al rotar se perdería la sesión con el robot, la telemetría y las órdenes en curso.
    // La Factory le pasa al ViewModel sus dependencias (el Repository) desde afuera.
    private val viewModel: RobotViewModel by viewModels { RobotViewModel.Factory }

    // En onCreate solo armamos la base: el ViewModel y la UI con setContent.
    // No hacemos acá ninguna carga de datos ni operación lenta, porque bloquearía el arranque
    // (pantalla negra). Los datos llegan después, de forma asíncrona, por Flow desde el Repository.
    // Tampoco sobreescribimos onStart/onStop: pausar la recolección de datos cuando la app pasa a
    // segundo plano lo resuelve collectAsStateWithLifecycle() en AppRoot.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // El tema elegido (claro/oscuro/sistema) también vive en el ViewModel y se lee con
            // collectAsStateWithLifecycle, así sobrevive a la rotación como el resto del estado.
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())

            // Edge-to-edge: la app dibuja detrás de las barras del sistema. Lo hacemos dentro de
            // setContent para que los íconos de la barra de estado y de navegación cambien de color
            // cada vez que el operador cambia de tema (si no, quedarían íconos claros sobre fondo claro).
            DisposableEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }

            RobotHmiTheme(darkTheme = dark) {
                AppRoot(viewModel)
            }
        }
    }
}
