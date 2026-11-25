package ar.edu.uade.robothmi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.robothmi.domain.Availability
import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.AlertSeverity
import ar.edu.uade.robothmi.model.CommandRecord
import ar.edu.uade.robothmi.model.CommandStatus
import ar.edu.uade.robothmi.ui.theme.Hmi
import ar.edu.uade.robothmi.ui.theme.Tone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Componentes reutilizables de la HMI.
 *
 * Ningún componente guarda estado propio: reciben los valores a mostrar y una función para avisar
 * los clics. Tampoco conocen al ViewModel ni al Repository. Por eso el mismo AlertCard o CommandButton
 * se usa en el Panel, en Control y en Alertas sin duplicar código.
 * El estado siempre se muestra con ícono + texto + color, nunca solo con color, para que se entienda
 * aunque el operador no distinga bien los colores. Los botones de orden miden al menos 64 dp de alto
 * para tocarlos sin errores, incluso con guantes.
 *
 * java.time (Instant, DateTimeFormatter) se usa directo porque minSdk = 26; con un minSdk menor
 * haría falta API desugaring.
 */
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatClock(ms: Long): String = clockFormat.format(Instant.ofEpochMilli(ms))
fun formatDateTime(ms: Long): String = dateFormat.format(Instant.ofEpochMilli(ms))

fun AlertSeverity.tone(): Tone = when (this) {
    AlertSeverity.INFO -> Tone.INFO
    AlertSeverity.WARNING -> Tone.WARN
    AlertSeverity.CRITICAL -> Tone.ERROR
}

fun AlertSeverity.icon(): ImageVector = when (this) {
    AlertSeverity.INFO -> Icons.Filled.Info
    AlertSeverity.WARNING -> Icons.Filled.Warning
    AlertSeverity.CRITICAL -> Icons.Filled.Error
}

fun CommandStatus.tone(): Tone = when (this) {
    CommandStatus.PENDING -> Tone.INFO
    CommandStatus.CONFIRMED -> Tone.OK
    CommandStatus.REJECTED -> Tone.ERROR
    CommandStatus.NO_RESPONSE -> Tone.WARN
}

fun CommandStatus.icon(): ImageVector = when (this) {
    CommandStatus.PENDING -> Icons.Filled.HourglassEmpty
    CommandStatus.CONFIRMED -> Icons.Filled.CheckCircle
    CommandStatus.REJECTED -> Icons.Filled.Block
    CommandStatus.NO_RESPONSE -> Icons.Filled.SignalWifiOff
}

/** Etiqueta de estado: ícono + texto + color, nunca solo color. */
@Composable
fun StatusChip(text: String, tone: Tone, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tone.color.copy(alpha = 0.14f))
            .border(1.dp, tone.color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(14.dp))
        Text(
            text,
            color = tone.color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderTone: Tone? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Hmi.Surface,
        border = BorderStroke(1.dp, borderTone?.color?.copy(alpha = 0.6f) ?: Hmi.Outline),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Hmi.TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * Un dato de telemetría. Si el dato es antiguo se atenúa para que no se lea como vigente.
 * Lo que muestra la app no siempre es el estado actual del robot: es lo último que informó. Atenuar el
 * valor le avisa al operador que no debe tomar decisiones como si ese número fuera de ahora.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    tone: Tone = Tone.MUTED,
    stale: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Hmi.Surface,
        border = BorderStroke(1.dp, if (tone == Tone.WARN || tone == Tone.ERROR) tone.color.copy(alpha = 0.6f) else Hmi.Outline),
    ) {
        Column(Modifier.padding(12.dp).alpha(if (stale) 0.5f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = Hmi.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tone == Tone.WARN || tone == Tone.ERROR) tone.color else Hmi.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Hmi.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Botón de orden. Cuando la orden no está habilitada muestra el motivo en lugar de
 * limitarse a quedar gris.
 *
 * El botón NO decide si está habilitado: recibe la decisión ya tomada en [availability], calculada
 * por CommandRules. Así la regla está en un solo lugar y todos los botones de una misma orden se
 * comportan igual. El botón solo avisa que el usuario quiere enviar la orden con [onClick].
 */
@Composable
fun CommandButton(
    label: String,
    icon: ImageVector,
    availability: Availability,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.ACCENT,
) {
    val enabled = availability.isEnabled
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = tone.color.copy(alpha = 0.18f),
            contentColor = tone.color,
            disabledContainerColor = Hmi.SurfaceHigh.copy(alpha = 0.6f),
            disabledContentColor = Hmi.TextMuted.copy(alpha = 0.7f),
        ),
        border = BorderStroke(1.dp, if (enabled) tone.color.copy(alpha = 0.55f) else Hmi.Outline),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (availability is Availability.Disabled) {
                Text(availability.reason, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CommandStatusChip(status: CommandStatus, modifier: Modifier = Modifier) {
    StatusChip(status.label, status.tone(), status.icon(), modifier)
}

/**
 * Una orden con su desenlace (pendiente/confirmada/rechazada/sin respuesta) y el motivo.
 * Sirve para que el operador revise qué pidió y qué contestó el robot en cada caso.
 */
@Composable
fun CommandRow(record: CommandRecord, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.type.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(record.target, "enviada ${formatClock(record.sentAtMs)}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Hmi.TextMuted,
                )
            }
            CommandStatusChip(record.status)
        }
        if (record.reason != null) {
            Text(
                record.reason,
                style = MaterialTheme.typography.bodySmall,
                color = record.status.tone().color,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Acción contextual de una alerta (por ejemplo, "Regresar a base" en batería baja). */
data class AlertAction(
    val label: String,
    val availability: Availability,
    val onClick: () -> Unit,
)

/**
 * Tarjeta de alerta. Está armada para que el operador pueda actuar sin pensar demasiado: el título dice
 * qué pasó, el detalle qué implica, y debajo están las acciones seguras posibles (por ejemplo
 * "Regresar a base"). Reconocer la alerta también es una orden al robot, con su propia disponibilidad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertCard(
    alert: Alert,
    ackAvailability: Availability,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<AlertAction> = emptyList(),
) {
    val tone = if (alert.acknowledged) Tone.MUTED else alert.severity.tone()
    Panel(modifier.fillMaxWidth(), borderTone = if (alert.acknowledged) null else tone) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(alert.severity.icon(), contentDescription = alert.severity.label, tint = tone.color)
                Column(Modifier.weight(1f)) {
                    Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alert.severity.label} · ${formatClock(alert.timestampMs)}" +
                            if (alert.acknowledged) " · reconocida" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                    )
                }
                if (alert.requiresDecision && !alert.acknowledged) StatusChip("Requiere decisión", Tone.ERROR)
            }
            Text(alert.detail, style = MaterialTheme.typography.bodyMedium, color = if (alert.acknowledged) Hmi.TextMuted else Hmi.Text)

            if (!alert.acknowledged) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.forEach { action ->
                        FilledTonalButton(onClick = action.onClick, enabled = action.availability.isEnabled) {
                            Text(action.label)
                        }
                    }
                    OutlinedButton(onClick = onAcknowledge, enabled = ackAvailability.isEnabled) {
                        Text("Reconocer")
                    }
                }
                val blocked = (actions.map { it.availability } + ackAvailability)
                    .filterIsInstance<Availability.Disabled>()
                    .map { it.reason }
                    .distinct()
                if (blocked.isNotEmpty()) {
                    Text(
                        "No disponible: ${blocked.joinToString("; ")}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Hmi.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Hmi.TextMuted, modifier = Modifier.size(36.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Hmi.TextMuted)
    }
}
