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
 *
 * Usamos un Repository para que sea la ÚNICA puerta de entrada a los datos del robot. El ViewModel
 * le pide cosas ("conectate", "enviá esta orden") y observa resultados, sin saber de dónde salen.
 * En nuestra app esto es clave porque hoy los datos vienen de un simulador y mañana vendrán de un
 * robot real por red: como el ViewModel depende de esta interfaz y no de una clase concreta, ese
 * cambio no obliga a tocar ni la UI ni el ViewModel.
 *
 * Cumple el mismo rol que un gateway entre la app y el SDK del robot: concentra toda la comunicación.
 * La telemetría funciona como una publicación periódica (como un Topic de ROS 2) y las órdenes como
 * una solicitud con respuesta asíncrona (como un Service/Action).
 *
 * Exponemos StateFlow para lo que tiene un valor actual (conexión, telemetría, alertas): quien se
 * suscribe recibe enseguida el último valor, así la pantalla nunca arranca vacía ni ambigua.
 * El historial es un Flow porque viene de Room y se vuelve a emitir cada vez que cambia la tabla.
 * Usamos tipos de Kotlin (Flow/StateFlow) y no LiveData para que la capa de datos no dependa de Android.
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

    /**
     * Historial persistido localmente.
     * Viene de un DAO de Room que devuelve Flow: cada vez que se registra o se borra un evento, Room
     * vuelve a emitir la lista. Por eso la pantalla de Historial se actualiza sola, sin botón "Recargar".
     */
    val history: Flow<List<HistoryEvent>>

    fun connect(robotId: String)
    fun disconnect()

    /**
     * Envía una orden. El resultado llega de forma asíncrona por [commands].
     *
     * No devuelve nada a propósito: enviar una orden no es lo mismo que el robot la acepte, ni que la
     * ejecute. Esta función solo la emite; la respuesta (aceptada, rechazada o sin respuesta) llega
     * después como un CommandStatus. Así la app nunca muestra "hecho" antes de que el robot conteste.
     */
    fun sendCommand(type: CommandType, missionId: String? = null, alertId: Long? = null)

    /** Registra en el historial una acción del operador que no es una orden al robot. */
    fun logOperatorAction(title: String, detail: String)

    fun clearHistory()
}

/**
 * Controles del escenario de demostración. Interfaz separada a propósito:
 * no forma parte del contrato con el robot y desaparece con una implementación real.
 *
 * La separamos de RobotRepository para que lo que existe solo para la demo (cortar el enlace,
 * forzar un obstáculo) no se mezcle con lo que la app le puede pedir a un robot de verdad.
 */
interface SimulationControls {
    val settings: StateFlow<SimulationSettings>
    fun setLinkDown(down: Boolean)
    fun setRejectCommands(reject: Boolean)
    fun forceLowBattery()
    fun forceObstacle()
    fun forceIntruder()
}
