package ar.edu.uade.robothmi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.uade.robothmi.RobotHmiApp
import ar.edu.uade.robothmi.data.RobotRepository
import ar.edu.uade.robothmi.data.SimulationControls
import ar.edu.uade.robothmi.data.local.ThemeSettings
import ar.edu.uade.robothmi.domain.Availability
import ar.edu.uade.robothmi.domain.CommandRules
import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.CommandRecord
import ar.edu.uade.robothmi.model.CommandStatus
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.HistoryEvent
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.RobotInfo
import ar.edu.uade.robothmi.model.SimulationSettings
import ar.edu.uade.robothmi.model.Telemetry
import ar.edu.uade.robothmi.model.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class UiState(
    val robots: List<RobotInfo> = emptyList(),
    val selectedRobot: RobotInfo? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val telemetry: Telemetry? = null,
    /** Antigüedad del último dato recibido. */
    val dataAgeMs: Long = 0,
    val stale: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val commands: List<CommandRecord> = emptyList(),
    val missions: List<MissionPlan> = emptyList(),
    val availability: Map<CommandType, Availability> = emptyMap(),
    val ackAvailability: Map<Long, Availability> = emptyMap(),
    val simulation: SimulationSettings = SimulationSettings(),
) {
    val activeAlerts: List<Alert> get() = alerts.filter { !it.acknowledged }
    val currentPlan: MissionPlan? get() = missions.firstOrNull { it.id == telemetry?.mission?.missionId }
    fun availabilityOf(type: CommandType): Availability =
        availability[type] ?: Availability.Disabled("Sin robot conectado")
}

/** Todo lo que la UI puede pedir. La UI no conoce ni al Repository ni al simulador. */
sealed interface UiEvent {
    data class SelectRobot(val robotId: String) : UiEvent
    data object Disconnect : UiEvent
    data class SendCommand(val type: CommandType, val missionId: String? = null, val alertId: Long? = null) : UiEvent
    data class NotifyGuard(val alert: Alert) : UiEvent
    data object ClearHistory : UiEvent
    data class SetTheme(val mode: ThemeMode) : UiEvent

    // Panel de simulación (solo para la demo)
    data class SimSetLinkDown(val down: Boolean) : UiEvent
    data class SimSetRejectCommands(val reject: Boolean) : UiEvent
    data object SimLowBattery : UiEvent
    data object SimObstacle : UiEvent
    data object SimIntruder : UiEvent
}

class RobotViewModel(
    private val repository: RobotRepository,
    private val simulation: SimulationControls,
    private val themeSettings: ThemeSettings,
) : ViewModel() {

    private data class Session(val robots: List<RobotInfo>, val selected: RobotInfo?, val connection: ConnectionState)
    private data class Live(val telemetry: Telemetry?, val alerts: List<Alert>, val commands: List<CommandRecord>)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(500)
        }
    }

    val uiState: StateFlow<UiState> = combine(
        combine(repository.robots, repository.selectedRobot, repository.connection, ::Session),
        combine(repository.telemetry, repository.alerts, repository.commands, ::Live),
        simulation.settings,
        ticker,
    ) { session, live, sim, now ->
        val age = live.telemetry?.let { (now - it.timestampMs).coerceAtLeast(0) } ?: 0
        val pending = live.commands.filter { it.status == CommandStatus.PENDING }
        val pendingTypes = pending.map { it.type }.toSet()
        val ackPending = CommandType.ACK_ALERT in pendingTypes
        UiState(
            robots = session.robots,
            selectedRobot = session.selected,
            connection = session.connection,
            telemetry = live.telemetry,
            dataAgeMs = age,
            stale = live.telemetry != null && (session.connection == ConnectionState.LOST || age > STALE_AFTER_MS),
            alerts = live.alerts,
            commands = live.commands,
            missions = repository.missions,
            availability = CommandType.entries.associateWith {
                CommandRules.availability(it, session.connection, live.telemetry, pendingTypes)
            },
            ackAvailability = live.alerts.associate {
                it.id to CommandRules.ackAvailability(it, session.connection, ackPending)
            },
            simulation = sim,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val history: StateFlow<List<HistoryEvent>> =
        repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tema elegido; MainActivity lo resuelve contra la configuración del dispositivo. */
    val themeMode: StateFlow<ThemeMode> = themeSettings.mode

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SelectRobot -> repository.connect(event.robotId)
            UiEvent.Disconnect -> repository.disconnect()
            is UiEvent.SendCommand -> send(event)
            is UiEvent.NotifyGuard -> repository.logOperatorAction(
                "Aviso a guardia registrado",
                "Por la alerta «${event.alert.title}». El aviso se hace por el canal habitual (radio/teléfono); la app solo lo deja asentado.",
            )
            UiEvent.ClearHistory -> repository.clearHistory()
            is UiEvent.SetTheme -> themeSettings.setMode(event.mode)
            is UiEvent.SimSetLinkDown -> simulation.setLinkDown(event.down)
            is UiEvent.SimSetRejectCommands -> simulation.setRejectCommands(event.reject)
            UiEvent.SimLowBattery -> simulation.forceLowBattery()
            UiEvent.SimObstacle -> simulation.forceObstacle()
            UiEvent.SimIntruder -> simulation.forceIntruder()
        }
    }

    private fun send(event: UiEvent.SendCommand) {
        // Segunda barrera: aunque la UI muestre el botón deshabilitado, se revalida acá.
        val state = uiState.value
        val allowed = if (event.type == CommandType.ACK_ALERT) {
            state.ackAvailability[event.alertId]?.isEnabled == true
        } else {
            state.availabilityOf(event.type).isEnabled
        }
        if (allowed) repository.sendCommand(event.type, event.missionId, event.alertId)
    }

    companion object {
        const val STALE_AFTER_MS = 3_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as RobotHmiApp).container
                RobotViewModel(container.repository, container.simulationControls, container.themeSettings)
            }
        }
    }
}
