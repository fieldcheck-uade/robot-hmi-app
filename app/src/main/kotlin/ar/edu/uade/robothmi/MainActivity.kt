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

class MainActivity : ComponentActivity() {
    private val viewModel: RobotViewModel by viewModels { RobotViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())

            // Íconos de la barra de estado y de navegación acordes al tema activo.
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
