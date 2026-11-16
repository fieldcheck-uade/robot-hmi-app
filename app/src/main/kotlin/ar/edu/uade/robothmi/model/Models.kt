package ar.edu.uade.robothmi.model

// ---------- Robot y misiones ----------

enum class RobotAvailability { AVAILABLE, MAINTENANCE }

data class RobotInfo(
    val id: String,
    val name: String,
    val model: String,
    val site: String,
    val availability: RobotAvailability,
    val initialBattery: Float,
)

data class Checkpoint(val id: String, val name: String, val x: Float, val y: Float)

data class MissionPlan(
    val id: String,
    val name: String,
    val description: String,
    /** Puntos de control que el robot inspecciona, en orden. */
    val checkpoints: List<Checkpoint>,
    val estimatedSeconds: Int,
    /** Recorrido completo entre la salida y el regreso a la base: puntos de control y esquinas de paso. */
    val waypoints: List<Checkpoint> = checkpoints,
)

// ---------- Estado ----------

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, LOST }

enum class OperationalState(val label: String) {
    IDLE("En espera"),
    PATROLLING("Patrullando"),
    PAUSED("En pausa"),
    WAITING_OPERATOR("Esperando decisión"),
    RETURNING("Regresando a base"),
    STOPPED("Detenido"),
}

enum class MissionState(val label: String) {
    RUNNING("En curso"),
    PAUSED("Pausada"),
    COMPLETED("Completada"),
    CANCELLED("Cancelada"),
    ABORTED("Abortada");

    val isActive: Boolean get() = this == RUNNING || this == PAUSED
}

data class MissionStatus(
    val missionId: String,
    val name: String,
    val state: MissionState,
    val progress: Float,
    val checkpointsDone: Int,
    val checkpointsTotal: Int,
    val nextCheckpoint: String?,
)

data class Telemetry(
    val robotId: String,
    val seq: Long,
    val timestampMs: Long,
    // Conectividad
    val signalPercent: Int,
    val latencyMs: Int,
    // Energía
    val batteryPercent: Float,
    val charging: Boolean,
    // Estado operativo y misión
    val opState: OperationalState,
    val mission: MissionStatus?,
    // Ubicación y movimiento
    val x: Float,
    val y: Float,
    val zone: String,
    val atSafePoint: Boolean,
    val headingDeg: Float,
    val speedMps: Float,
    // Variables del servicio de vigilancia
    val ambientTempC: Float,
    val checkpointsInspectedShift: Int,
    val anomaliesDetected: Int,
)

// ---------- Alertas ----------

enum class AlertSeverity(val label: String) {
    INFO("Info"),
    WARNING("Advertencia"),
    CRITICAL("Crítica"),
}

enum class AlertType { LOW_BATTERY, CRITICAL_BATTERY, OBSTACLE, INTRUDER, CONNECTION_LOST }

data class Alert(
    val id: Long,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val detail: String,
    val timestampMs: Long,
    val acknowledged: Boolean = false,
    /** La situación exige que el operador elija cómo seguir. */
    val requiresDecision: Boolean = false,
)

// ---------- Comandos ----------

enum class CommandType(val label: String, val wire: String) {
    START_MISSION("Iniciar misión", "start_mission"),
    PAUSE_MISSION("Pausar misión", "pause_mission"),
    RESUME_MISSION("Reanudar misión", "resume_mission"),
    CANCEL_MISSION("Cancelar misión", "cancel_mission"),
    RETURN_TO_SAFE_POINT("Regresar a punto seguro", "return_to_safe_point"),
    OPERATIONAL_STOP("Detención operativa", "operational_stop"),
    ACK_ALERT("Reconocer alerta", "ack_alert"),
}

enum class CommandStatus(val label: String) {
    PENDING("Pendiente"),
    CONFIRMED("Confirmada"),
    REJECTED("Rechazada"),
    NO_RESPONSE("Sin respuesta"),
}

data class CommandRecord(
    val id: Long,
    val type: CommandType,
    val target: String?,
    val sentAtMs: Long,
    val status: CommandStatus,
    val resolvedAtMs: Long? = null,
    val reason: String? = null,
)

// ---------- Historial ----------

enum class HistoryCategory(val label: String) {
    MISSION("Misiones"),
    COMMAND("Órdenes"),
    ALERT("Alertas"),
    CONNECTION("Conexión"),
    OPERATOR("Operador"),
}

data class HistoryEvent(
    val id: Long,
    val timestampMs: Long,
    val robotName: String,
    val category: HistoryCategory,
    val severity: AlertSeverity,
    val title: String,
    val detail: String,
)

// ---------- Simulación ----------

data class SimulationSettings(
    val linkDown: Boolean = false,
    val rejectCommands: Boolean = false,
)
