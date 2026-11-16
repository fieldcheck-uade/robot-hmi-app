package ar.edu.uade.robothmi.domain

import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.AlertType
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.MissionState
import ar.edu.uade.robothmi.model.OperationalState
import ar.edu.uade.robothmi.model.Telemetry

sealed interface Availability {
    data object Enabled : Availability
    data class Disabled(val reason: String) : Availability

    val isEnabled: Boolean get() = this is Enabled
}

/**
 * Condiciones de habilitación de cada orden del lado de la interfaz.
 *
 * Son una primera barrera para evitar órdenes sin sentido; la autoridad final es el robot,
 * que puede rechazar igualmente una orden habilitada (ver RobotSimulator.handle).
 */
object CommandRules {
    const val MIN_BATTERY_TO_START = 25f

    fun availability(
        type: CommandType,
        connection: ConnectionState,
        telemetry: Telemetry?,
        pending: Set<CommandType>,
    ): Availability {
        if (telemetry == null || connection == ConnectionState.DISCONNECTED || connection == ConnectionState.CONNECTING) {
            return Availability.Disabled("Sin robot conectado")
        }
        if (type in pending) return Availability.Disabled("Orden pendiente de respuesta")

        val lost = connection == ConnectionState.LOST
        val mission = telemetry.mission
        val missionActive = mission?.state?.isActive == true

        // Sin enlace solo quedan disponibles las órdenes de seguridad.
        if (lost && type != CommandType.OPERATIONAL_STOP && type != CommandType.RETURN_TO_SAFE_POINT) {
            return Availability.Disabled("Sin conexión: datos desactualizados")
        }

        return when (type) {
            CommandType.START_MISSION -> when {
                missionActive -> Availability.Disabled("Ya hay una misión en curso")
                telemetry.opState != OperationalState.IDLE -> Availability.Disabled("El robot no está en espera")
                !telemetry.atSafePoint -> Availability.Disabled("El robot debe estar en la base")
                telemetry.batteryPercent < MIN_BATTERY_TO_START ->
                    Availability.Disabled("Batería insuficiente (mín. ${MIN_BATTERY_TO_START.toInt()} %)")
                else -> Availability.Enabled
            }

            CommandType.PAUSE_MISSION ->
                if (telemetry.opState == OperationalState.PATROLLING) Availability.Enabled
                else Availability.Disabled("No hay misión en ejecución")

            CommandType.RESUME_MISSION ->
                if (mission?.state == MissionState.PAUSED) Availability.Enabled
                else Availability.Disabled("No hay misión pausada")

            CommandType.CANCEL_MISSION ->
                if (missionActive) Availability.Enabled
                else Availability.Disabled("No hay misión activa")

            CommandType.RETURN_TO_SAFE_POINT -> when {
                telemetry.atSafePoint -> Availability.Disabled("Ya está en el punto seguro")
                telemetry.opState == OperationalState.RETURNING -> Availability.Disabled("Ya está regresando")
                else -> Availability.Enabled
            }

            CommandType.OPERATIONAL_STOP -> when {
                // Con datos viejos no se puede saber si se mueve: la detención queda siempre disponible.
                lost -> Availability.Enabled
                telemetry.opState == OperationalState.IDLE || telemetry.opState == OperationalState.STOPPED ->
                    Availability.Disabled("El robot ya está detenido")
                else -> Availability.Enabled
            }

            CommandType.ACK_ALERT -> Availability.Enabled
        }
    }

    fun ackAvailability(alert: Alert, connection: ConnectionState, ackPending: Boolean): Availability = when {
        alert.acknowledged -> Availability.Disabled("Alerta ya reconocida")
        ackPending -> Availability.Disabled("Orden pendiente de respuesta")
        // La alerta de enlace la genera la propia app: se reconoce localmente.
        alert.type == AlertType.CONNECTION_LOST -> Availability.Enabled
        connection != ConnectionState.CONNECTED -> Availability.Disabled("Sin conexión con el robot")
        else -> Availability.Enabled
    }
}
