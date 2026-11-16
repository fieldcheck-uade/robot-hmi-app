package ar.edu.uade.robothmi.data

import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.CommandRecord
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.HistoryEvent
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.RobotInfo
import ar.edu.uade.robothmi.model.SimulationSettings
import ar.edu.uade.robothmi.model.Telemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Única puerta de la app hacia el robot: telemetría, comandos y eventos.
 *
 * El ViewModel solo conoce esta interfaz. Hoy la implementa SimulatedRobotRepository;
 * una implementación real (WebSocket/DDS contra un Go2) la reemplazaría sin tocar UI ni ViewModel.
 */
interface RobotRepository {
    val robots: StateFlow<List<RobotInfo>>
    val missions: List<MissionPlan>

    val selectedRobot: StateFlow<RobotInfo?>
    val connection: StateFlow<ConnectionState>

    /** Último mensaje de telemetría recibido. No se actualiza mientras el enlace está caído. */
    val telemetry: StateFlow<Telemetry?>
    val alerts: StateFlow<List<Alert>>

    /** Órdenes de la sesión, la más reciente primero, con su estado de respuesta. */
    val commands: StateFlow<List<CommandRecord>>

    /** Historial persistido localmente. */
    val history: Flow<List<HistoryEvent>>

    fun connect(robotId: String)
    fun disconnect()

    /** Envía una orden. El resultado llega de forma asíncrona por [commands]. */
    fun sendCommand(type: CommandType, missionId: String? = null, alertId: Long? = null)

    /** Registra en el historial una acción del operador que no es una orden al robot. */
    fun logOperatorAction(title: String, detail: String)

    fun clearHistory()
}

/**
 * Controles del escenario de demostración. Interfaz separada a propósito:
 * no forma parte del contrato con el robot y desaparece con una implementación real.
 */
interface SimulationControls {
    val settings: StateFlow<SimulationSettings>
    fun setLinkDown(down: Boolean)
    fun setRejectCommands(reject: Boolean)
    fun forceLowBattery()
    fun forceObstacle()
    fun forceIntruder()
}
