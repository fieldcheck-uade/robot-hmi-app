package ar.edu.uade.robothmi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.AlertType
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.OperationalState
import ar.edu.uade.robothmi.model.Telemetry
import ar.edu.uade.robothmi.ui.UiEvent
import ar.edu.uade.robothmi.ui.UiState
import ar.edu.uade.robothmi.ui.components.AlertAction
import ar.edu.uade.robothmi.ui.components.AlertCard
import ar.edu.uade.robothmi.ui.components.MetricTile
import ar.edu.uade.robothmi.ui.components.Panel
import ar.edu.uade.robothmi.ui.components.PatrolMap
import ar.edu.uade.robothmi.ui.components.SectionTitle
import ar.edu.uade.robothmi.ui.components.StatusChip
import ar.edu.uade.robothmi.ui.components.formatClock
import ar.edu.uade.robothmi.ui.theme.Hmi
import ar.edu.uade.robothmi.ui.theme.Tone
import kotlin.math.roundToInt

// Color de cada estado del robot. El `when` cubre todos los casos del enum sin `else`: si mañana se
// agrega un estado nuevo, el código no compila hasta que se le asigne un color acá.
fun OperationalState.tone(): Tone = when (this) {
    OperationalState.IDLE -> Tone.MUTED
    OperationalState.PATROLLING -> Tone.OK
    OperationalState.PAUSED -> Tone.WARN
    OperationalState.WAITING_OPERATOR -> Tone.ERROR
    OperationalState.RETURNING -> Tone.INFO
    OperationalState.STOPPED -> Tone.ERROR
}

fun Telemetry.stateLabel(): String = when {
    opState == OperationalState.IDLE && atSafePoint && charging -> "En base · cargando"
    opState == OperationalState.IDLE && atSafePoint -> "En base · listo"
    opState == OperationalState.IDLE -> "En espera fuera de base"
    else -> opState.label
}

/**
 * Color of the operational state title.
 * Old data is shown muted, an idle robot uses the normal text color,
 * and any other state uses the color of its tone.
 */
@Composable
private fun operationalStateColor(telemetry: Telemetry, isDataStale: Boolean): Color {
    if (isDataStale) {
        return Hmi.TextMuted
    }
    if (telemetry.opState == OperationalState.IDLE) {
        return Hmi.Text
    }
    return telemetry.opState.tone().color
}

/**
 * Acciones que la interfaz ofrece junto a cada alerta, según su tipo.
 * Junto a cada alerta ofrecemos las salidas seguras para esa situación (regresar, reanudar, cancelar,
 * avisar a guardia), así el operador resuelve desde la misma tarjeta sin ir a otra pestaña.
 * Cada acción respeta la disponibilidad que calculó CommandRules.
 */
fun alertActions(alert: Alert, state: UiState, request: (UiEvent.SendCommand) -> Unit, onEvent: (UiEvent) -> Unit): List<AlertAction> {
    fun command(label: String, type: CommandType) =
        AlertAction(label, state.availabilityOf(type)) { request(UiEvent.SendCommand(type)) }
    return when (alert.type) {
        AlertType.LOW_BATTERY -> listOf(command("Regresar a base", CommandType.RETURN_TO_SAFE_POINT))
        AlertType.OBSTACLE -> listOf(
            command("Reanudar por ruta alternativa", CommandType.RESUME_MISSION),
            command("Regresar a base", CommandType.RETURN_TO_SAFE_POINT),
            command("Cancelar misión", CommandType.CANCEL_MISSION),
        )
        AlertType.INTRUDER -> listOf(
            AlertAction("Avisar a guardia", ar.edu.uade.robothmi.domain.Availability.Enabled) { onEvent(UiEvent.NotifyGuard(alert)) },
            command("Falsa alarma: reanudar", CommandType.RESUME_MISSION),
            command("Regresar a base", CommandType.RETURN_TO_SAFE_POINT),
        )
        AlertType.UNSUPERVISED_PAUSE -> listOf(
            command("Reanudar ronda", CommandType.RESUME_MISSION),
            command("Regresar a base", CommandType.RETURN_TO_SAFE_POINT),
            command("Cancelar misión", CommandType.CANCEL_MISSION),
        )
        AlertType.CRITICAL_BATTERY, AlertType.CONNECTION_LOST -> emptyList()
    }
}

/**
 * Panel principal de supervisión.
 *
 * El orden de arriba hacia abajo responde a lo que el operador necesita saber primero:
 *  1) si tiene que actuar (la alerta más grave), 2) qué está haciendo el robot y en qué misión,
 *  3) progreso y ubicación en el mapa, 4) el detalle técnico (telemetría) al final.
 * No guarda estado: dibuja lo que recibe en `state` y avisa las acciones con `request`/`onEvent`.
 */
@Composable
fun DashboardScreen(state: UiState, request: (UiEvent.SendCommand) -> Unit, onEvent: (UiEvent) -> Unit) {
    val t = state.telemetry ?: return
    val stale = state.stale
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // La alerta más grave sin reconocer queda arriba de todo, con sus acciones.
        state.activeAlerts.maxByOrNull { it.severity.ordinal * 10 + (if (it.requiresDecision) 1 else 0) }?.let { alert ->
            AlertCard(
                alert = alert,
                ackAvailability = state.ackAvailability[alert.id] ?: state.availabilityOf(CommandType.ACK_ALERT),
                onAcknowledge = { request(UiEvent.SendCommand(CommandType.ACK_ALERT, alertId = alert.id)) },
                actions = alertActions(alert, state, request, onEvent),
            )
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Estado operativo", style = MaterialTheme.typography.labelMedium, color = Hmi.TextMuted)
                        Text(
                            t.stateLabel(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = operationalStateColor(telemetry = t, isDataStale = stale),
                        )
                    }
                    if (stale) StatusChip("Dato antiguo", Tone.WARN, Icons.Filled.Update)
                }
                val mission = t.mission
                if (mission == null) {
                    Text("Sin misión asignada en esta sesión.", style = MaterialTheme.typography.bodyMedium, color = Hmi.TextMuted)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mission.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${mission.state.label} · ${(mission.progress * 100).roundToInt()} %",
                            style = MaterialTheme.typography.labelLarge,
                            color = Hmi.TextMuted,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { mission.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (stale) Hmi.TextMuted else Hmi.Accent,
                        trackColor = Hmi.SurfaceHigh,
                    )
                    Text(
                        "Puntos de control ${mission.checkpointsDone}/${mission.checkpointsTotal}" +
                            (mission.nextCheckpoint?.let { " · próximo: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(if (stale) "Ubicación · última posición conocida" else "Ubicación")
                PatrolMap(telemetry = t, plan = state.currentPlan, stale = stale)
                Text(
                    "▢ base de carga (punto seguro)   ○ punto de control   ● inspeccionado",
                    style = MaterialTheme.typography.bodySmall,
                    color = Hmi.TextMuted,
                )
            }
        }

        // Telemetría agrupada por tema: energía, comunicación, movimiento y ubicación.
        // Con `stale` cada dato se atenúa, porque es el último valor conocido y no el actual.
        SectionTitle("Telemetría")
        TileRow {
            MetricTile(
                label = "Batería",
                value = "${t.batteryPercent.roundToInt()}",
                unit = "%",
                caption = when {
                    t.charging -> "Cargando"
                    t.batteryPercent < 10 -> "Crítica"
                    t.batteryPercent < 20 -> "Baja"
                    else -> "Normal"
                },
                icon = if (t.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                tone = when {
                    t.batteryPercent < 10 -> Tone.ERROR
                    t.batteryPercent < 20 -> Tone.WARN
                    else -> Tone.OK
                },
                stale = stale,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Señal",
                value = "${t.signalPercent}",
                unit = "%",
                caption = "Latencia ${t.latencyMs} ms",
                icon = Icons.Filled.Wifi,
                tone = if (t.signalPercent < 35) Tone.WARN else Tone.OK,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            MetricTile(
                label = "Velocidad",
                value = "%.1f".format(t.speedMps),
                unit = "m/s",
                icon = Icons.Filled.Speed,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Rumbo",
                value = "${t.headingDeg.roundToInt()}°",
                caption = compass(t.headingDeg),
                icon = Icons.Filled.Explore,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            MetricTile(
                label = "Zona",
                value = t.zone,
                icon = Icons.Filled.Place,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Posición",
                value = "%.0f, %.0f".format(t.x, t.y),
                unit = "m",
                icon = Icons.Filled.MyLocation,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle("Servicio de vigilancia")
        TileRow {
            MetricTile(
                label = "Puntos inspeccionados",
                value = "${t.checkpointsInspectedShift}",
                caption = "En este turno",
                icon = Icons.Filled.TaskAlt,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Anomalías",
                value = "${t.anomaliesDetected}",
                caption = "Detectadas en el turno",
                icon = Icons.Filled.ReportProblem,
                tone = if (t.anomaliesDetected > 0) Tone.WARN else Tone.MUTED,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            MetricTile(
                label = "Temperatura ambiente",
                value = "%.1f".format(t.ambientTempC),
                unit = "°C",
                icon = Icons.Filled.Thermostat,
                stale = stale,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Última actualización",
                value = formatClock(t.timestampMs),
                caption = "Hace ${state.dataAgeMs / 1000} s",
                icon = Icons.Filled.Update,
                tone = if (stale) Tone.WARN else Tone.MUTED,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

private fun compass(deg: Float): String {
    val names = listOf("Norte", "Noreste", "Este", "Sudeste", "Sur", "Sudoeste", "Oeste", "Noroeste")
    return names[(((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}
