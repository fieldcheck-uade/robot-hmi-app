package ar.edu.uade.robothmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.edu.uade.robothmi.ui.AppRoot
import ar.edu.uade.robothmi.ui.RobotViewModel
import ar.edu.uade.robothmi.ui.theme.RobotHmiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RobotViewModel by viewModels { RobotViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            RobotHmiTheme {
                AppRoot(viewModel)
            }
        }
    }
}
