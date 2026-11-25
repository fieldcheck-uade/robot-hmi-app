package ar.edu.uade.robothmi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.RobotAvailability
import ar.edu.uade.robothmi.model.RobotInfo
import ar.edu.uade.robothmi.ui.UiEvent
import ar.edu.uade.robothmi.ui.UiState
import ar.edu.uade.robothmi.ui.components.StatusChip
import ar.edu.uade.robothmi.ui.components.ThemeToggleButton
import ar.edu.uade.robothmi.ui.theme.Hmi
import ar.edu.uade.robothmi.ui.theme.Tone

/**
 * Pantalla de entrada: elegir el robot a supervisar.
 *
 * No se conecta a nada por su cuenta: al tocar un robot envía UiEvent.SelectRobot y la conexión la
 * hace el Repository. La pantalla solo refleja en qué estado está cada robot.
 * Cada robot muestra un estado claro (Disponible / Conectando… / En mantenimiento) con texto e ícono.
 * Los que están en mantenimiento se ven atenuados y no se pueden tocar, así el operador no intenta
 * conectarse a un equipo que no va a responder.
 */
@Composable
fun RobotSelectScreen(state: UiState, onEvent: (UiEvent) -> Unit) {
    val connecting = state.connection == ConnectionState.CONNECTING
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding() // edge-to-edge (MainActivity): no dibujar debajo de las barras del sistema
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Hmi.Accent, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("Centinela Go2", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Rondas de vigilancia nocturna", style = MaterialTheme.typography.bodyMedium, color = Hmi.TextMuted)
            }
            ThemeToggleButton { onEvent(UiEvent.SetTheme(it)) }
        }
        Text(
            "Elegí el robot que vas a supervisar",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        state.robots.forEach { robot ->
            RobotCard(
                robot = robot,
                connecting = connecting && state.selectedRobot?.id == robot.id,
                enabled = !connecting && robot.availability == RobotAvailability.AVAILABLE,
                onClick = { onEvent(UiEvent.SelectRobot(robot.id)) },
            )
        }

        Text(
            "Robot simulado: la app no se conecta a ningún equipo real ni usa internet.",
            style = MaterialTheme.typography.bodySmall,
            color = Hmi.TextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RobotCard(robot: RobotInfo, connecting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val available = robot.availability == RobotAvailability.AVAILABLE
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Hmi.Surface,
        border = BorderStroke(1.dp, if (connecting) Hmi.Accent else Hmi.Outline),
        modifier = Modifier.fillMaxWidth().alpha(if (available) 1f else 0.6f),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.SmartToy, contentDescription = null,
                tint = if (available) Hmi.Accent else Hmi.TextMuted, modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(robot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(robot.model, style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted)
                Text(robot.site, style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted)
            }
            when {
                connecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Conectando…", style = MaterialTheme.typography.labelMedium, color = Hmi.Accent)
                }
                available -> StatusChip("Disponible", Tone.OK, Icons.Filled.CheckCircle)
                else -> StatusChip("En mantenimiento", Tone.MUTED, Icons.Filled.Build)
            }
        }
    }
}
