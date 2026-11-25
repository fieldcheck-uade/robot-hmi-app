package ar.edu.uade.robothmi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.SiteMap
import ar.edu.uade.robothmi.model.Telemetry
import ar.edu.uade.robothmi.ui.theme.Hmi

/**
 * Plano esquemático de la nave con el recorrido de la ronda y la posición del robot.
 * Con datos antiguos el robot se dibuja hueco: es la última posición conocida, no la actual.
 *
 * Con el mapa el operador ve de un vistazo dónde está el robot y hacia dónde va, sin leer coordenadas.
 * Pasa los metros del plano (SiteMap) a píxeles con un factor de escala.
 * Un Canvas no tiene texto que pueda leer un lector de pantalla, por eso le agregamos una descripción
 * con `semantics { contentDescription }`.
 * rememberTextMeasurer se guarda entre recomposiciones para no recrearlo cada vez que cambia la telemetría.
 */
@Composable
fun PatrolMap(telemetry: Telemetry?, plan: MissionPlan?, stale: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = Hmi.palette
    val labelStyle = TextStyle(color = Hmi.TextMuted, fontSize = 9.sp)
    val description = telemetry?.let {
        "Mapa de la nave. Robot en ${it.zone}" + if (stale) ", última posición conocida" else ""
    } ?: "Mapa de la nave"

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(SiteMap.WIDTH / SiteMap.HEIGHT)
            .semantics { contentDescription = description },
    ) {
        val scale = size.width / SiteMap.WIDTH
        fun at(x: Float, y: Float) = Offset(x * scale, y * scale)

        drawRoundRect(colors.background, cornerRadius = CornerRadius(8f * density))
        drawRoundRect(colors.outline, cornerRadius = CornerRadius(8f * density), style = Stroke(1.5f * density))

        SiteMap.zones.forEach { zone ->
            val topLeft = at(zone.left, zone.top)
            val zoneSize = Size((zone.right - zone.left) * scale, (zone.bottom - zone.top) * scale)
            drawRect(colors.surfaceHigh.copy(alpha = 0.7f), topLeft, zoneSize)
            drawRect(colors.outline, topLeft, zoneSize, style = Stroke(1f * density))
            val text = measurer.measure(zone.name, labelStyle, maxLines = 2, softWrap = true,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = (zoneSize.width - 8 * density).toInt().coerceAtLeast(1)))
            drawText(text, topLeft = topLeft + Offset(4f * density, 3f * density))
        }

        // Recorrido de la misión
        val mission = telemetry?.mission
        if (plan != null && mission != null) {
            val path = Path().apply {
                moveTo(SiteMap.BASE_X * scale, SiteMap.BASE_Y * scale)
                plan.waypoints.forEach { lineTo(it.x * scale, it.y * scale) }
                lineTo(SiteMap.BASE_X * scale, SiteMap.BASE_Y * scale)
            }
            drawPath(
                path, colors.accent.copy(alpha = 0.7f),
                style = Stroke(1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 5f * density))),
            )
            plan.checkpoints.forEachIndexed { index, cp ->
                val done = index < mission.checkpointsDone
                val center = at(cp.x, cp.y)
                drawCircle(colors.background, 5f * density, center)
                if (done) drawCircle(colors.ok, 5f * density, center)
                else drawCircle(colors.accent, 5f * density, center, style = Stroke(1.5f * density))
            }
        }

        // Punto seguro (base de carga)
        val base = at(SiteMap.BASE_X, SiteMap.BASE_Y)
        val half = 5f * density
        drawRect(colors.info, base - Offset(half, half), Size(half * 2, half * 2), style = Stroke(2f * density))

        // Robot
        if (telemetry != null) {
            val pos = at(telemetry.x, telemetry.y)
            val color = if (stale) colors.warn else colors.text
            rotate(telemetry.headingDeg, pivot = pos) {
                val r = 7f * density
                val arrow = Path().apply {
                    moveTo(pos.x, pos.y - r * 1.4f)
                    lineTo(pos.x + r, pos.y + r)
                    lineTo(pos.x, pos.y + r * 0.4f)
                    lineTo(pos.x - r, pos.y + r)
                    close()
                }
                if (stale) drawPath(arrow, color, style = Stroke(1.5f * density))
                else drawPath(arrow, color)
            }
        }
    }
}
