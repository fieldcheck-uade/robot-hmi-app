package ar.edu.uade.robothmi.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.HistoryCategory
import ar.edu.uade.robothmi.model.HistoryEvent
import ar.edu.uade.robothmi.ui.UiEvent
import ar.edu.uade.robothmi.ui.UiState
import ar.edu.uade.robothmi.ui.components.AlertCard
import ar.edu.uade.robothmi.ui.components.CommandButton
import ar.edu.uade.robothmi.ui.components.CommandRow
import ar.edu.uade.robothmi.ui.components.EmptyState
import ar.edu.uade.robothmi.ui.components.Panel
import ar.edu.uade.robothmi.ui.components.SectionTitle
import ar.edu.uade.robothmi.ui.components.StatusChip
import ar.edu.uade.robothmi.ui.components.formatDateTime
import ar.edu.uade.robothmi.ui.components.icon
import ar.edu.uade.robothmi.ui.components.tone
import ar.edu.uade.robothmi.ui.theme.Hmi
import ar.edu.uade.robothmi.ui.theme.Tone
import kotlin.math.roundToInt

// ---------- Misiones ----------

@Composable
fun MissionsScreen(state: UiState, request: (UiEvent.SendCommand) -> Unit) {
    val mission = state.telemetry?.mission
    val start = state.availabilityOf(CommandType.START_MISSION)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Misión actual")
        Panel(Modifier.fillMaxWidth()) {
            if (mission == null) {
                Text("Todavía no se inició ninguna misión.", color = Hmi.TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mission.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusChip(
                            mission.state.label,
                            when {
                                state.stale -> Tone.MUTED
                                mission.state.isActive -> Tone.ACCENT
                                mission.state == ar.edu.uade.robothmi.model.MissionState.COMPLETED -> Tone.OK
                                else -> Tone.WARN
                            },
                        )
                    }
                    LinearProgressIndicator(
                        progress = { mission.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (state.stale) Hmi.TextMuted else Hmi.Accent,
                        trackColor = Hmi.SurfaceHigh,
                    )
                    Text(
                        "${(mission.progress * 100).roundToInt()} % · puntos ${mission.checkpointsDone}/${mission.checkpointsTotal}" +
                            (mission.nextCheckpoint?.let { " · próximo: $it" } ?: "") +
                            if (state.stale) " · dato antiguo" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CommandButton(
                            "Pausar", Icons.Filled.Pause, state.availabilityOf(CommandType.PAUSE_MISSION),
                            onClick = { request(UiEvent.SendCommand(CommandType.PAUSE_MISSION)) },
                            modifier = Modifier.weight(1f),
                        )
                        CommandButton(
                            "Reanudar", Icons.Filled.PlayArrow, state.availabilityOf(CommandType.RESUME_MISSION),
                            onClick = { request(UiEvent.SendCommand(CommandType.RESUME_MISSION)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CommandButton(
                        "Cancelar misión", Icons.Filled.Cancel, state.availabilityOf(CommandType.CANCEL_MISSION),
                        onClick = { request(UiEvent.SendCommand(CommandType.CANCEL_MISSION)) },
                        modifier = Modifier.fillMaxWidth(),
                        tone = Tone.WARN,
                    )
                }
            }
        }

        SectionTitle("Rondas disponibles")
        state.missions.forEach { plan ->
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Route, contentDescription = null, tint = Hmi.Accent)
                        Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = Hmi.TextMuted)
                    Text(
                        "${plan.checkpoints.size} puntos de control · ~${plan.estimatedSeconds} s (tiempo de simulación)\n" +
                            plan.checkpoints.joinToString(" → ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                    )
                    CommandButton(
                        "Iniciar esta ronda", Icons.Filled.PlayArrow, start,
                        onClick = { request(UiEvent.SendCommand(CommandType.START_MISSION, missionId = plan.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------- Control ----------

@Composable
fun ControlScreen(state: UiState, request: (UiEvent.SendCommand) -> Unit, goToMissions: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommandButton(
            "Detención operativa", Icons.Filled.PanTool, state.availabilityOf(CommandType.OPERATIONAL_STOP),
            onClick = { request(UiEvent.SendCommand(CommandType.OPERATIONAL_STOP)) },
            modifier = Modifier.fillMaxWidth(),
            tone = Tone.ERROR,
        )
        Text(
            "La detención es una orden de software: puede no llegar o ser rechazada y no reemplaza la parada de " +
                "emergencia física del robot. Una orden confirmada significa que el robot la aceptó; verificá en la " +
                "telemetría que efectivamente se ejecutó.",
            style = MaterialTheme.typography.bodySmall,
            color = Hmi.TextMuted,
        )

        SectionTitle("Misión")
        val start = state.availabilityOf(CommandType.START_MISSION)
        CommandButton(
            "Iniciar misión (elegir ronda)", Icons.Filled.Route, start,
            onClick = goToMissions,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommandButton(
                "Pausar", Icons.Filled.Pause, state.availabilityOf(CommandType.PAUSE_MISSION),
                onClick = { request(UiEvent.SendCommand(CommandType.PAUSE_MISSION)) },
                modifier = Modifier.weight(1f),
            )
            CommandButton(
                "Reanudar", Icons.Filled.PlayArrow, state.availabilityOf(CommandType.RESUME_MISSION),
                onClick = { request(UiEvent.SendCommand(CommandType.RESUME_MISSION)) },
                modifier = Modifier.weight(1f),
            )
        }
        CommandButton(
            "Cancelar misión", Icons.Filled.Cancel, state.availabilityOf(CommandType.CANCEL_MISSION),
            onClick = { request(UiEvent.SendCommand(CommandType.CANCEL_MISSION)) },
            modifier = Modifier.fillMaxWidth(),
            tone = Tone.WARN,
        )

        SectionTitle("Seguridad")
        CommandButton(
            "Regresar a punto seguro", Icons.Filled.Home, state.availabilityOf(CommandType.RETURN_TO_SAFE_POINT),
            onClick = { request(UiEvent.SendCommand(CommandType.RETURN_TO_SAFE_POINT)) },
            modifier = Modifier.fillMaxWidth(),
            tone = Tone.INFO,
        )

        SectionTitle("Órdenes de esta sesión")
        Panel(Modifier.fillMaxWidth()) {
            if (state.commands.isEmpty()) {
                Text("Todavía no se envió ninguna orden.", color = Hmi.TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    state.commands.forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(color = Hmi.Outline)
                        CommandRow(record)
                    }
                }
            }
        }
    }
}

// ---------- Alertas ----------

@Composable
fun AlertsScreen(state: UiState, request: (UiEvent.SendCommand) -> Unit, onEvent: (UiEvent) -> Unit) {
    if (state.alerts.isEmpty()) {
        EmptyState("Sin alertas en esta sesión.", Icons.Filled.NotificationsNone)
        return
    }
    val (acknowledged, active) = state.alerts.partition { it.acknowledged }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Activas (${active.size})") }
        if (active.isEmpty()) {
            item { Text("No hay alertas pendientes de reconocer.", color = Hmi.TextMuted, style = MaterialTheme.typography.bodyMedium) }
        }
        items(active, key = { it.id }) { alert ->
            AlertCard(
                alert = alert,
                ackAvailability = state.ackAvailability[alert.id] ?: state.availabilityOf(CommandType.ACK_ALERT),
                onAcknowledge = { request(UiEvent.SendCommand(CommandType.ACK_ALERT, alertId = alert.id)) },
                actions = alertActions(alert, state, request, onEvent),
            )
        }
        if (acknowledged.isNotEmpty()) {
            item { SectionTitle("Reconocidas (${acknowledged.size})") }
            items(acknowledged, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    ackAvailability = state.ackAvailability[alert.id] ?: state.availabilityOf(CommandType.ACK_ALERT),
                    onAcknowledge = {},
                )
            }
        }
    }
}

// ---------- Historial ----------

@Composable
fun HistoryScreen(history: List<HistoryEvent>, onEvent: (UiEvent) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<HistoryCategory?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val shown = history.filter { filter == null || it.category == filter }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todo") })
            HistoryCategory.entries.forEach { category ->
                FilterChip(selected = filter == category, onClick = { filter = category }, label = { Text(category.label) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shown.size} eventos guardados en el dispositivo",
                style = MaterialTheme.typography.bodySmall,
                color = Hmi.TextMuted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Borrar historial") }
        }
        if (shown.isEmpty()) {
            EmptyState("No hay eventos para mostrar.", Icons.Filled.History)
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(shown, key = { it.id }) { event ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(event.severity.icon(), contentDescription = event.severity.label, tint = event.severity.tone().color)
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (event.detail.isNotBlank()) {
                                Text(event.detail, style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted)
                            }
                            Text(
                                "${formatDateTime(event.timestampMs)} · ${event.category.label} · ${event.robotName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Hmi.TextMuted,
                            )
                        }
                    }
                    HorizontalDivider(color = Hmi.Outline)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminan todos los eventos guardados en este dispositivo. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onEvent(UiEvent.ClearHistory) }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Volver") } },
        )
    }
}
