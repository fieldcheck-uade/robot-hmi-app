package ar.edu.uade.robothmi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.robothmi.model.CommandStatus
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.HistoryEvent
import ar.edu.uade.robothmi.model.OperationalState
import ar.edu.uade.robothmi.ui.components.StatusChip
import ar.edu.uade.robothmi.ui.components.formatClock
import ar.edu.uade.robothmi.ui.components.icon
import ar.edu.uade.robothmi.ui.components.tone
import ar.edu.uade.robothmi.ui.screens.AlertsScreen
import ar.edu.uade.robothmi.ui.screens.ControlScreen
import ar.edu.uade.robothmi.ui.screens.DashboardScreen
import ar.edu.uade.robothmi.ui.screens.HistoryScreen
import ar.edu.uade.robothmi.ui.screens.MissionsScreen
import ar.edu.uade.robothmi.ui.screens.RobotSelectScreen
import ar.edu.uade.robothmi.ui.theme.Hmi
import ar.edu.uade.robothmi.ui.theme.Tone

private enum class Tab(val label: String, val icon: ImageVector) {
    PANEL("Panel", Icons.Filled.Dashboard),
    MISSIONS("Misiones", Icons.Filled.Route),
    CONTROL("Control", Icons.Filled.Gamepad),
    ALERTS("Alertas", Icons.Filled.Notifications),
    HISTORY("Historial", Icons.Filled.History),
}

@Composable
fun AppRoot(viewModel: RobotViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = Hmi.Background) {
        val inSession = state.telemetry != null &&
            (state.connection == ConnectionState.CONNECTED || state.connection == ConnectionState.LOST)
        if (inSession) {
            MainScreen(state, history, viewModel::onEvent)
        } else {
            RobotSelectScreen(state, viewModel::onEvent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: UiState, history: List<HistoryEvent>, onEvent: (UiEvent) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.PANEL) }
    var toConfirm by remember { mutableStateOf<UiEvent.SendCommand?>(null) }
    var showSimPanel by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    // Las órdenes con consecuencias difíciles de revertir piden confirmación.
    // La detención no: tiene que salir con un solo toque.
    val request: (UiEvent.SendCommand) -> Unit = { command ->
        when (command.type) {
            CommandType.START_MISSION, CommandType.CANCEL_MISSION, CommandType.RETURN_TO_SAFE_POINT -> toConfirm = command
            else -> onEvent(command)
        }
    }

    BackHandler(enabled = tab != Tab.PANEL) { tab = Tab.PANEL }

    Scaffold(
        containerColor = Hmi.Background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Hmi.Background),
                    title = {
                        Column {
                            Text(
                                state.selectedRobot?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.connection == ConnectionState.LOST) {
                                StatusChip("Sin conexión", Tone.ERROR, Icons.Filled.SignalWifiOff)
                            } else {
                                StatusChip("Conectado", Tone.OK, Icons.Filled.CheckCircle)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSimPanel = true }) {
                            Icon(Icons.Filled.Science, contentDescription = "Panel de simulación", tint = Hmi.TextMuted)
                        }
                        IconButton(onClick = { confirmDisconnect = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Desconectar del robot", tint = Hmi.TextMuted)
                        }
                    },
                )
                if (state.stale) StaleBanner(state)
                CommandStrip(state, onStop = { onEvent(UiEvent.SendCommand(CommandType.OPERATIONAL_STOP)) })
                HorizontalDivider(color = Hmi.Outline)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Hmi.Surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            val count = state.activeAlerts.size
                            if (item == Tab.ALERTS && count > 0) {
                                BadgedBox(badge = { Badge { Text("$count") } }) { Icon(item.icon, contentDescription = null) }
                            } else {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.PANEL -> DashboardScreen(state, request, onEvent)
                Tab.MISSIONS -> MissionsScreen(state, request)
                Tab.CONTROL -> ControlScreen(state, request, goToMissions = { tab = Tab.MISSIONS })
                Tab.ALERTS -> AlertsScreen(state, request, onEvent)
                Tab.HISTORY -> HistoryScreen(history, onEvent)
            }
        }
    }

    toConfirm?.let { command ->
        val missionName = state.missions.firstOrNull { it.id == command.missionId }?.name
        val (title, text) = when (command.type) {
            CommandType.START_MISSION -> "¿Iniciar la ronda?" to
                "El robot va a salir de la base y recorrer «$missionName». Verificá que no haya personas en el recorrido."
            CommandType.CANCEL_MISSION -> "¿Cancelar la misión?" to
                "La ronda no se puede retomar después de cancelada. El robot queda detenido donde está."
            else -> "¿Regresar al punto seguro?" to
                "El robot abandona la tarea actual y vuelve a la base de carga. La misión en curso se da por abortada."
        }
        AlertDialog(
            onDismissRequest = { toConfirm = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { toConfirm = null; onEvent(command) }) { Text("Enviar orden") }
            },
            dismissButton = { TextButton(onClick = { toConfirm = null }) { Text("Volver") } },
        )
    }

    if (confirmDisconnect) {
        val moving = state.telemetry?.opState.let { it == OperationalState.PATROLLING || it == OperationalState.RETURNING }
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("¿Desconectarse del robot?") },
            text = {
                Text(
                    if (moving) "El robot está en movimiento y va a quedar sin supervisión desde esta app."
                    else "Se cierra la sesión de supervisión. El historial queda guardado."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDisconnect = false; onEvent(UiEvent.Disconnect) }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Volver") } },
        )
    }

    if (showSimPanel) {
        ModalBottomSheet(onDismissRequest = { showSimPanel = false }, containerColor = Hmi.Surface) {
            SimulationPanel(state, onEvent, close = { showSimPanel = false })
        }
    }
}

@Composable
private fun StaleBanner(state: UiState) {
    Row(
        Modifier.fillMaxWidth().background(Hmi.Error.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.SignalWifiOff, contentDescription = null, tint = Hmi.Error, modifier = Modifier.size(18.dp))
        Text(
            "Sin datos del robot hace ${state.dataAgeMs / 1000} s. Lo que ves es el último estado conocido" +
                (state.telemetry?.let { " (${formatClock(it.timestampMs)})" } ?: "") + ".",
            style = MaterialTheme.typography.bodySmall,
            color = Hmi.Text,
        )
    }
}

/** Franja fija: estado de la última orden y acceso a la detención desde cualquier pestaña. */
@Composable
private fun CommandStrip(state: UiState, onStop: () -> Unit) {
    val last = state.commands.firstOrNull()
    val stop = state.availabilityOf(CommandType.OPERATIONAL_STOP)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (last == null) {
                Text("Sin órdenes enviadas", style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted)
            } else {
                if (last.status == CommandStatus.PENDING) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Hmi.Info)
                } else {
                    Icon(last.status.icon(), contentDescription = null, tint = last.status.tone().color, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        "${last.type.label}: ${last.status.label.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = last.status.tone().color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        last.reason ?: when (last.status) {
                            CommandStatus.PENDING -> "Esperando respuesta del robot…"
                            else -> "Aceptada por el robot ${formatClock(last.resolvedAtMs ?: last.sentAtMs)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Hmi.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Button(
            onClick = onStop,
            enabled = stop.isEnabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Hmi.Error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = Hmi.SurfaceHigh,
                disabledContentColor = Hmi.TextMuted,
            ),
        ) {
            Icon(Icons.Filled.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Detener", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SimulationPanel(state: UiState, onEvent: (UiEvent) -> Unit, close: () -> Unit) {
    val opState = state.telemetry?.opState
    val moving = opState == OperationalState.PATROLLING || opState == OperationalState.RETURNING
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Panel de simulación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Solo para la demostración: fuerza situaciones en el robot simulado. No existiría en una app conectada a un robot real.",
            style = MaterialTheme.typography.bodySmall,
            color = Hmi.TextMuted,
        )
        SwitchRow(
            "Cortar el enlace", "La telemetría deja de llegar y las órdenes quedan sin respuesta.",
            state.simulation.linkDown,
        ) { onEvent(UiEvent.SimSetLinkDown(it)) }
        SwitchRow(
            "Rechazar órdenes", "El robot responde con rechazo a todo lo que se le envíe.",
            state.simulation.rejectCommands,
        ) { onEvent(UiEvent.SimSetRejectCommands(it)) }
        HorizontalDivider(color = Hmi.Outline)
        OutlinedButton(onClick = { onEvent(UiEvent.SimLowBattery); close() }, modifier = Modifier.fillMaxWidth()) {
            Text("Forzar batería baja")
        }
        OutlinedButton(onClick = { onEvent(UiEvent.SimObstacle); close() }, enabled = moving, modifier = Modifier.fillMaxWidth()) {
            Text("Poner un obstáculo en el recorrido")
        }
        OutlinedButton(
            onClick = { onEvent(UiEvent.SimIntruder); close() },
            enabled = opState == OperationalState.PATROLLING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Persona detectada fuera de horario")
        }
        if (!moving) {
            Text(
                "El obstáculo y la detección de persona necesitan que el robot esté en movimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = Hmi.TextMuted,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
