package ar.edu.uade.robothmi.data.sim

import ar.edu.uade.robothmi.model.AlertSeverity
import ar.edu.uade.robothmi.model.AlertType
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.MissionState
import ar.edu.uade.robothmi.model.MissionStatus
import ar.edu.uade.robothmi.model.OperationalState
import ar.edu.uade.robothmi.model.RobotInfo
import ar.edu.uade.robothmi.model.SiteMap
import ar.edu.uade.robothmi.model.Telemetry
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Lo que el robot simulado le cuenta a la app además de la telemetría periódica. */
sealed interface SimEvent {
    val timestampMs: Long

    data class AlertRaised(
        override val timestampMs: Long,
        val type: AlertType,
        val severity: AlertSeverity,
        val title: String,
        val detail: String,
        val requiresDecision: Boolean,
    ) : SimEvent

    data class MissionChanged(
        override val timestampMs: Long,
        val severity: AlertSeverity,
        val title: String,
        val detail: String,
    ) : SimEvent
}

sealed interface CommandOutcome {
    data object Accepted : CommandOutcome
    data class Rejected(val reason: String) : CommandOutcome
}

/**
 * Modelo del Go2 de vigilancia. Kotlin puro, sin corrutinas ni Android: se avanza con [tick]
 * y se le entregan órdenes con [handle], lo que permite probarlo con tests unitarios.
 *
 * No es seguro entre hilos: quien lo usa debe serializar las llamadas.
 */
class RobotSimulator(
    private val robot: RobotInfo,
    private val missions: List<MissionPlan>,
    private val random: Random = Random.Default,
) {
    private class Point(val x: Float, val y: Float, val name: String, val inspect: Boolean)

    private class ActiveMission(val plan: MissionPlan) {
        val path: List<Point> = buildList {
            val inspected = plan.checkpoints.map { it.id }.toSet()
            add(Point(SiteMap.BASE_X, SiteMap.BASE_Y, SiteMap.SAFE_POINT_NAME, inspect = false))
            plan.waypoints.forEach { add(Point(it.x, it.y, it.name, inspect = it.id in inspected)) }
            add(Point(SiteMap.BASE_X, SiteMap.BASE_Y, SiteMap.SAFE_POINT_NAME, inspect = false))
        }
        val totalLength: Float = path.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        var state = MissionState.RUNNING
        var reachedIndex = 0
        var travelled = 0f
        var dwellLeft = 0f
        var checkpointsDone = 0
    }

    var battery: Float = robot.initialBattery
        private set
    var state: OperationalState = OperationalState.IDLE
        private set

    private var x = SiteMap.BASE_X
    private var y = SiteMap.BASE_Y
    private var heading = 0f
    private var speed = 0f
    private var mission: ActiveMission? = null
    private var lowBatteryWarned = false
    private var criticalBatteryHandled = false
    private var anomalies = 0
    private var checkpointsInspectedShift = 0
    private var seq = 0L
    private var elapsed = 0f
    private val events = mutableListOf<SimEvent>()

    private val atBase: Boolean get() = SiteMap.distanceToBase(x, y) < 0.5f
    private val missionActive: Boolean get() = mission?.state?.isActive == true

    // ---------- Avance del tiempo ----------

    fun tick(dtSeconds: Float, nowMs: Long) {
        elapsed += dtSeconds
        when (state) {
            OperationalState.PATROLLING -> advanceMission(dtSeconds, nowMs)
            OperationalState.RETURNING -> {
                val arrived = moveToward(SiteMap.BASE_X, SiteMap.BASE_Y, CRUISE_SPEED * dtSeconds)
                speed = CRUISE_SPEED
                if (arrived) {
                    state = OperationalState.IDLE
                    speed = 0f
                    events += SimEvent.MissionChanged(
                        nowMs, AlertSeverity.INFO, "Robot en punto seguro", "Llegó a la base de carga.",
                    )
                }
            }
            else -> speed = 0f
        }
        updateBattery(dtSeconds, nowMs)
    }

    private fun advanceMission(dt: Float, nowMs: Long) {
        val m = mission ?: return
        if (m.dwellLeft > 0f) {
            // Detenido en un punto de control mientras lo inspecciona.
            speed = 0f
            m.dwellLeft -= dt
            if (m.dwellLeft <= 0f) {
                m.checkpointsDone++
                checkpointsInspectedShift++
            }
            return
        }
        val target = m.path[m.reachedIndex + 1]
        val before = hypot(target.x - x, target.y - y)
        val arrived = moveToward(target.x, target.y, CRUISE_SPEED * dt)
        m.travelled += if (arrived) before else CRUISE_SPEED * dt
        speed = CRUISE_SPEED
        if (arrived) {
            m.reachedIndex++
            if (m.reachedIndex == m.path.lastIndex) {
                m.state = MissionState.COMPLETED
                state = OperationalState.IDLE
                speed = 0f
                events += SimEvent.MissionChanged(
                    nowMs, AlertSeverity.INFO, "Misión completada",
                    "${m.plan.name}: ${m.checkpointsDone}/${m.plan.checkpoints.size} puntos inspeccionados.",
                )
            } else if (target.inspect) {
                m.dwellLeft = CHECKPOINT_DWELL
            }
        }
    }

    /** @return true si llegó al destino. */
    private fun moveToward(tx: Float, ty: Float, step: Float): Boolean {
        val dx = tx - x
        val dy = ty - y
        val dist = hypot(dx, dy)
        if (dist > 0.01f) {
            // 0° = norte (arriba en el plano), sentido horario.
            heading = ((Math.toDegrees(atan2(dx, -dy).toDouble()).toFloat()) + 360f) % 360f
        }
        if (dist <= step) {
            x = tx
            y = ty
            return true
        }
        x += dx / dist * step
        y += dy / dist * step
        return false
    }

    private fun updateBattery(dt: Float, nowMs: Long) {
        val charging = atBase && state == OperationalState.IDLE
        battery = when {
            charging -> battery + CHARGE_RATE * dt
            speed > 0f -> battery - DRAIN_MOVING * dt
            else -> battery - DRAIN_IDLE * dt
        }.coerceIn(1f, 100f)

        if (battery > LOW_BATTERY + 10f) {
            lowBatteryWarned = false
            criticalBatteryHandled = false
        }
        if (battery < LOW_BATTERY && !lowBatteryWarned) {
            lowBatteryWarned = true
            events += SimEvent.AlertRaised(
                nowMs, AlertType.LOW_BATTERY, AlertSeverity.WARNING,
                "Batería baja (${battery.roundToInt()} %)",
                "Se recomienda ordenar el regreso al punto seguro. Por debajo de ${CRITICAL_BATTERY.toInt()} % el robot regresa solo.",
                requiresDecision = false,
            )
        }
        if (battery < CRITICAL_BATTERY && !criticalBatteryHandled) {
            criticalBatteryHandled = true
            val wasAway = !atBase
            if (wasAway) {
                abortMission(nowMs, "batería crítica")
                state = OperationalState.RETURNING
            }
            events += SimEvent.AlertRaised(
                nowMs, AlertType.CRITICAL_BATTERY, AlertSeverity.CRITICAL,
                "Batería crítica (${battery.roundToInt()} %)",
                if (wasAway) "El robot abortó la tarea y regresa a la base por su cuenta."
                else "El robot permanece en la base cargando.",
                requiresDecision = false,
            )
        }
    }

    private fun abortMission(nowMs: Long, cause: String) {
        val m = mission ?: return
        if (!m.state.isActive) return
        m.state = MissionState.ABORTED
        events += SimEvent.MissionChanged(
            nowMs, AlertSeverity.WARNING, "Misión abortada", "${m.plan.name}: abortada por $cause.",
        )
    }

    // ---------- Órdenes ----------

    /** El robot decide si acepta la orden según su estado real, no según lo que crea la app. */
    fun handle(type: CommandType, missionId: String?, nowMs: Long): CommandOutcome {
        when (type) {
            CommandType.START_MISSION -> {
                val plan = missions.firstOrNull { it.id == missionId }
                    ?: return CommandOutcome.Rejected("Misión desconocida")
                if (missionActive) return CommandOutcome.Rejected("Ya hay una misión en curso")
                if (state != OperationalState.IDLE) return CommandOutcome.Rejected("El robot no está en espera")
                if (!atBase) return CommandOutcome.Rejected("El robot debe partir desde la base")
                if (battery < MIN_BATTERY_TO_START) {
                    return CommandOutcome.Rejected("Batería insuficiente (${battery.roundToInt()} %)")
                }
                mission = ActiveMission(plan)
                state = OperationalState.PATROLLING
                events += SimEvent.MissionChanged(nowMs, AlertSeverity.INFO, "Misión iniciada", plan.name)
            }

            CommandType.PAUSE_MISSION -> {
                if (state != OperationalState.PATROLLING) return CommandOutcome.Rejected("No hay misión en ejecución")
                mission?.state = MissionState.PAUSED
                state = OperationalState.PAUSED
                events += SimEvent.MissionChanged(nowMs, AlertSeverity.INFO, "Misión pausada", mission?.plan?.name.orEmpty())
            }

            CommandType.RESUME_MISSION -> {
                val m = mission
                if (m == null || m.state != MissionState.PAUSED) return CommandOutcome.Rejected("No hay misión pausada")
                m.state = MissionState.RUNNING
                state = OperationalState.PATROLLING
                events += SimEvent.MissionChanged(nowMs, AlertSeverity.INFO, "Misión reanudada", m.plan.name)
            }

            CommandType.CANCEL_MISSION -> {
                val m = mission
                if (m == null || !m.state.isActive) return CommandOutcome.Rejected("No hay misión activa")
                m.state = MissionState.CANCELLED
                if (state != OperationalState.STOPPED) state = OperationalState.IDLE
                events += SimEvent.MissionChanged(
                    nowMs, AlertSeverity.WARNING, "Misión cancelada",
                    "${m.plan.name}: cancelada por el operador. El robot queda en el lugar.",
                )
            }

            CommandType.RETURN_TO_SAFE_POINT -> {
                if (atBase) return CommandOutcome.Rejected("El robot ya está en el punto seguro")
                if (state == OperationalState.RETURNING) return CommandOutcome.Rejected("El robot ya está regresando")
                abortMission(nowMs, "regreso a punto seguro")
                state = OperationalState.RETURNING
            }

            CommandType.OPERATIONAL_STOP -> {
                if (state == OperationalState.IDLE || state == OperationalState.STOPPED) {
                    return CommandOutcome.Rejected("El robot ya está detenido")
                }
                mission?.let { if (it.state == MissionState.RUNNING) it.state = MissionState.PAUSED }
                state = OperationalState.STOPPED
                speed = 0f
                events += SimEvent.MissionChanged(
                    nowMs, AlertSeverity.WARNING, "Detención operativa", "El robot se detuvo por orden del operador.",
                )
            }

            // El reconocimiento de alertas no cambia el estado físico del robot.
            CommandType.ACK_ALERT -> Unit
        }
        return CommandOutcome.Accepted
    }

    // ---------- Escenarios forzados ----------

    fun forceLowBattery() {
        battery = battery.coerceAtMost(LOW_BATTERY - 0.5f)
    }

    /** @return false si el robot no se está moviendo y por lo tanto no hay nada que bloquear. */
    fun forceObstacle(nowMs: Long): Boolean {
        if (state != OperationalState.PATROLLING && state != OperationalState.RETURNING) return false
        val wasReturning = state == OperationalState.RETURNING
        interruptForDecision()
        events += SimEvent.AlertRaised(
            nowMs, AlertType.OBSTACLE, AlertSeverity.CRITICAL,
            "Obstáculo bloquea el recorrido",
            "Pallet atravesado en ${SiteMap.zoneAt(x, y)}. El robot no encontró un desvío y espera una decisión: " +
                if (wasReturning) "reintentar el regreso o dejarlo detenido." else "reanudar por ruta alternativa, regresar o cancelar.",
            requiresDecision = true,
        )
        return true
    }

    /** Escenario propio del servicio: detección de una persona fuera de horario. */
    fun forceIntruder(nowMs: Long): Boolean {
        if (state != OperationalState.PATROLLING) return false
        interruptForDecision()
        anomalies++
        events += SimEvent.AlertRaised(
            nowMs, AlertType.INTRUDER, AlertSeverity.CRITICAL,
            "Persona detectada fuera de horario",
            "Detección en ${SiteMap.zoneAt(x, y)}. El robot se detuvo a distancia y mantiene la observación. " +
                "No se guardan imágenes: verificar y decidir si se avisa a la guardia.",
            requiresDecision = true,
        )
        return true
    }

    private fun interruptForDecision() {
        mission?.let { if (it.state == MissionState.RUNNING) it.state = MissionState.PAUSED }
        state = OperationalState.WAITING_OPERATOR
        speed = 0f
    }

    // ---------- Telemetría ----------

    fun snapshot(nowMs: Long): Telemetry {
        val distBase = SiteMap.distanceToBase(x, y)
        val zone = SiteMap.zoneAt(x, y)
        val m = mission
        return Telemetry(
            robotId = robot.id,
            seq = ++seq,
            timestampMs = nowMs,
            signalPercent = (96f - distBase * 0.7f + random.nextInt(-3, 4)).roundToInt().coerceIn(15, 100),
            latencyMs = (22f + distBase * 0.9f + random.nextInt(0, 15)).roundToInt(),
            batteryPercent = battery,
            charging = atBase && state == OperationalState.IDLE && battery < 100f,
            opState = state,
            mission = m?.let {
                MissionStatus(
                    missionId = it.plan.id,
                    name = it.plan.name,
                    state = it.state,
                    progress = if (it.state == MissionState.COMPLETED) 1f else (it.travelled / it.totalLength).coerceIn(0f, 1f),
                    checkpointsDone = it.checkpointsDone,
                    checkpointsTotal = it.plan.checkpoints.size,
                    nextCheckpoint = if (it.state.isActive) {
                        it.path.drop(it.reachedIndex + 1).firstOrNull { p -> p.inspect }?.name ?: SiteMap.SAFE_POINT_NAME
                    } else null,
                )
            },
            x = x,
            y = y,
            zone = zone,
            atSafePoint = atBase,
            headingDeg = heading,
            speedMps = if (speed > 0f) speed + random.nextFloat() * 0.1f - 0.05f else 0f,
            ambientTempC = 19f + 2f * sin(elapsed / 40f) + (if (zone == "Muelles de carga") -3f else 0f),
            checkpointsInspectedShift = checkpointsInspectedShift,
            anomaliesDetected = anomalies,
        )
    }

    fun drainEvents(): List<SimEvent> {
        val out = events.toList()
        events.clear()
        return out
    }

    companion object {
        const val CRUISE_SPEED = 1.5f // m/s
        const val CHECKPOINT_DWELL = 2f // s
        const val DRAIN_MOVING = 0.12f // %/s (acelerado para que la demo sea corta)
        const val DRAIN_IDLE = 0.02f
        const val CHARGE_RATE = 0.6f
        const val LOW_BATTERY = 20f
        const val CRITICAL_BATTERY = 10f
        const val MIN_BATTERY_TO_START = 25f
    }
}
