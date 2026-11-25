package ar.edu.uade.robothmi.data.sim

import ar.edu.uade.robothmi.data.RobotRepository
import ar.edu.uade.robothmi.data.SimulationControls
import ar.edu.uade.robothmi.data.local.HistoryDao
import ar.edu.uade.robothmi.data.local.HistoryEventEntity
import ar.edu.uade.robothmi.model.Alert
import ar.edu.uade.robothmi.model.AlertSeverity
import ar.edu.uade.robothmi.model.AlertType
import ar.edu.uade.robothmi.model.CommandRecord
import ar.edu.uade.robothmi.model.CommandStatus
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.HistoryCategory
import ar.edu.uade.robothmi.model.HistoryEvent
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.RobotAvailability
import ar.edu.uade.robothmi.model.RobotInfo
import ar.edu.uade.robothmi.model.SimulationSettings
import ar.edu.uade.robothmi.model.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Implementación simulada del Repository: reemplaza al robot, a la red y al servidor.
 *
 * Simula también el canal: la telemetría llega a 1 Hz, las respuestas tardan, el enlace
 * puede caerse (la telemetría deja de llegar y las órdenes quedan sin respuesta) y el robot
 * puede rechazar órdenes. Todo el estado mutable se toca desde un único hilo lógico.
 *
 * Simulamos a propósito los problemas de una red real: respuestas que tardan, cortes de enlace,
 * datos que envejecen y órdenes que nunca reciben respuesta. Lo hacemos para que la app tenga que
 * manejarlos: en un robot, mostrar una lectura vieja como si fuera actual puede ser tan peligroso
 * como no mostrar nada. Por eso existe el estado LOST y la alerta de "Conexión perdida".
 */
class SimulatedRobotRepository(
    private val historyDao: HistoryDao,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val clock: () -> Long = System::currentTimeMillis,
) : RobotRepository, SimulationControls {

    // Scope de corrutinas propio del Repository, que vive lo mismo que la app. No usamos viewModelScope
    // porque la sesión con el robot no tiene que cortarse si se destruye una pantalla.
    // SupervisorJob: si falla una corrutina (por ejemplo, una orden), las demás siguen funcionando.
    // limitedParallelism(1): las corrutinas corren de a una, como en un único hilo. Así el estado
    // compartido (sim, nextId, etc.) no necesita locks. Y nada corre en el hilo de la UI, que no se traba.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Patrón que se repite abajo: `_x` es un MutableStateFlow PRIVADO (solo este Repository lo modifica)
    // y `x` es la versión de solo lectura que ve el ViewModel. Así nadie de afuera puede cambiar,
    // por ejemplo, el estado de conexión: solo lo cambia quien sabe lo que pasó con el enlace.

    private val _robots = MutableStateFlow(SimData.robots)
    override val robots: StateFlow<List<RobotInfo>> = _robots.asStateFlow()
    override val missions: List<MissionPlan> = SimData.missions

    private val _selectedRobot = MutableStateFlow<RobotInfo?>(null)
    override val selectedRobot: StateFlow<RobotInfo?> = _selectedRobot.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    override val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    override val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val commands: StateFlow<List<CommandRecord>> = _commands.asStateFlow()

    private val _settings = MutableStateFlow(SimulationSettings())
    override val settings: StateFlow<SimulationSettings> = _settings.asStateFlow()

    // Convertimos cada fila de Room (HistoryEventEntity) en el modelo de la app (HistoryEvent, con enums).
    // Así la UI nunca depende de cómo está guardado en la base: si mañana cambia la tabla, solo se toca
    // esta conversión. runCatching evita un crash si una fila trae un valor que ya no existe en el enum.
    override val history: Flow<List<HistoryEvent>> = historyDao.observeRecent().map { rows ->
        rows.map {
            HistoryEvent(
                id = it.id,
                timestampMs = it.timestampMs,
                robotName = it.robotName,
                category = runCatching { HistoryCategory.valueOf(it.category) }.getOrDefault(HistoryCategory.OPERATOR),
                severity = runCatching { AlertSeverity.valueOf(it.severity) }.getOrDefault(AlertSeverity.INFO),
                title = it.title,
                detail = it.detail,
            )
        }
    }

    private var sim: RobotSimulator? = null
    private var session: Job? = null
    private var lastRxAtMs = 0L
    private var linkLostAtMs = 0L
    private var nextId = 1L

    // ---------- Conexión ----------

    // La conexión pasa por estados explícitos: DISCONNECTED → CONNECTING → CONNECTED (y LOST si se corta).
    // La UI usa ese estado para mostrar "Conectando…" o bloquear órdenes. Las validaciones del principio
    // evitan conectarse a un robot en mantenimiento o abrir dos sesiones a la vez.
    override fun connect(robotId: String) {
        scope.launch {
            val robot = _robots.value.firstOrNull { it.id == robotId } ?: return@launch
            if (robot.availability != RobotAvailability.AVAILABLE) return@launch
            if (_connection.value != ConnectionState.DISCONNECTED) return@launch

            _selectedRobot.value = robot
            _connection.value = ConnectionState.CONNECTING
            session = launch {
                delay(CONNECT_DELAY_MS)
                val simulator = RobotSimulator(robot, missions)
                sim = simulator
                _alerts.value = emptyList()
                _commands.value = emptyList()
                _settings.value = SimulationSettings()
                lastRxAtMs = clock()
                _telemetry.value = simulator.snapshot(lastRxAtMs)
                _connection.value = ConnectionState.CONNECTED
                log(HistoryCategory.CONNECTION, AlertSeverity.INFO, "Conexión establecida", "${robot.name} (${robot.model})")
                runTelemetryLoop(simulator)
            }
        }
    }

    override fun disconnect() {
        scope.launch {
            if (_selectedRobot.value == null) return@launch
            session?.cancel()
            session = null
            if (sim != null) {
                log(HistoryCategory.CONNECTION, AlertSeverity.INFO, "Desconexión", "El operador cerró la sesión con el robot.")
            }
            sim = null
            _connection.value = ConnectionState.DISCONNECTED
            _telemetry.value = null
            _alerts.value = emptyList()
            _commands.value = emptyList()
            _settings.value = SimulationSettings()
            _selectedRobot.value = null
        }
    }

    /**
     * Bucle que recibe la telemetría una vez por segundo y vigila el enlace (watchdog).
     * Si pasan LINK_TIMEOUT_MS sin datos, la conexión pasa a LOST y se genera una alerta. Lo hacemos
     * porque, sin este control, la pantalla seguiría mostrando el último dato como si estuviera
     * "conectado y operativo" aunque el robot ya esté en otro lugar.
     * El robot simulado sigue avanzando aunque la app no lo vea, igual que un robot real con autonomía.
     */
    private suspend fun runTelemetryLoop(simulator: RobotSimulator) {
        while (scope.isActive) {
            delay(TICK_MS)
            val now = clock()
            // El robot sigue funcionando aunque la app no lo vea; él también nota el corte.
            simulator.setOperatorLink(!_settings.value.linkDown)
            simulator.tick(TICK_MS / 1000f, now)

            if (!_settings.value.linkDown) {
                if (_connection.value == ConnectionState.LOST) onLinkRecovered(now)
                lastRxAtMs = now
                _telemetry.value = simulator.snapshot(now)
                // Lo ocurrido durante el corte llega junto al reconectar.
                simulator.drainEvents().forEach { deliver(it) }
            } else if (_connection.value == ConnectionState.CONNECTED && now - lastRxAtMs >= LINK_TIMEOUT_MS) {
                onLinkLost(now)
            }
        }
    }

    private fun onLinkLost(now: Long) {
        _connection.value = ConnectionState.LOST
        linkLostAtMs = lastRxAtMs
        val alert = Alert(
            id = nextId++,
            type = AlertType.CONNECTION_LOST,
            severity = AlertSeverity.CRITICAL,
            title = "Conexión perdida",
            detail = "Sin telemetría hace más de ${LINK_TIMEOUT_MS / 1000} s. Los datos en pantalla son antiguos " +
                "y las órdenes pueden no llegar. Si el corte dura ${RobotSimulator.LINK_LOSS_PAUSE_S.toInt()} s, " +
                "el robot pausa la ronda en el lugar y espera; con batería crítica regresa solo a la base.",
            timestampMs = now,
        )
        _alerts.update { listOf(alert) + it }
        log(HistoryCategory.CONNECTION, AlertSeverity.CRITICAL, alert.title, "Último dato recibido a las ${clockText(lastRxAtMs)}.")
    }

    private fun onLinkRecovered(now: Long) {
        _connection.value = ConnectionState.CONNECTED
        val seconds = (now - linkLostAtMs) / 1000
        _alerts.update { list ->
            list.map {
                if (it.type == AlertType.CONNECTION_LOST && !it.acknowledged) {
                    it.copy(acknowledged = true, detail = "Enlace recuperado tras $seconds s sin datos.")
                } else it
            }
        }
        log(HistoryCategory.CONNECTION, AlertSeverity.INFO, "Conexión recuperada", "Tras $seconds s sin datos.")
    }

    private fun deliver(event: SimEvent) {
        when (event) {
            is SimEvent.AlertRaised -> {
                val alert = Alert(
                    id = nextId++,
                    type = event.type,
                    severity = event.severity,
                    title = event.title,
                    detail = event.detail,
                    timestampMs = event.timestampMs,
                    requiresDecision = event.requiresDecision,
                )
                _alerts.update { listOf(alert) + it }
                log(HistoryCategory.ALERT, event.severity, event.title, event.detail, event.timestampMs)
            }
            is SimEvent.MissionChanged ->
                log(HistoryCategory.MISSION, event.severity, event.title, event.detail, event.timestampMs)
        }
    }

    // ---------- Órdenes ----------

    /**
     * Ciclo de vida de una orden. Distinguimos enviar, aceptar y ejecutar:
     * 1. Se registra como PENDING: la app la envió, pero todavía no sabe nada.
     * 2. Tras una demora simulada el robot responde: CONFIRMED (la aceptó) o REJECTED (con el motivo).
     * 3. Si el enlace sigue caído después de COMMAND_TIMEOUT_MS, queda NO_RESPONSE: no sabemos si llegó,
     *    y la app lo dice así en vez de suponer que se ejecutó.
     * Aun confirmada, eso solo significa que el robot la aceptó: que efectivamente se movió se ve en la telemetría.
     */
    override fun sendCommand(type: CommandType, missionId: String?, alertId: Long?) {
        scope.launch {
            val simulator = sim ?: return@launch
            val alert = alertId?.let { id -> _alerts.value.firstOrNull { it.id == id } }
            val target = when (type) {
                CommandType.START_MISSION -> missions.firstOrNull { it.id == missionId }?.name
                CommandType.ACK_ALERT -> alert?.title
                else -> null
            }
            val record = CommandRecord(id = nextId++, type = type, target = target, sentAtMs = clock(), status = CommandStatus.PENDING)
            _commands.update { (listOf(record) + it).take(MAX_COMMANDS) }

            // La alerta de enlace es de la propia app: no viaja al robot.
            if (type == CommandType.ACK_ALERT && alert?.type == AlertType.CONNECTION_LOST) {
                acknowledge(alert.id)
                resolve(record, CommandStatus.CONFIRMED, "Reconocida localmente")
                return@launch
            }

            delay(Random.nextLong(RESPONSE_MIN_MS, RESPONSE_MAX_MS))
            var waited = 0L
            while (_settings.value.linkDown && waited < COMMAND_TIMEOUT_MS) {
                delay(250)
                waited += 250
            }
            if (sim !== simulator) return@launch // la sesión terminó mientras esperaba

            when {
                _settings.value.linkDown ->
                    resolve(record, CommandStatus.NO_RESPONSE, "Sin respuesta del robot en ${COMMAND_TIMEOUT_MS / 1000} s. No se sabe si la orden llegó.")

                _settings.value.rejectCommands ->
                    resolve(record, CommandStatus.REJECTED, "El robot rechazó la orden: controlador ocupado (rechazo forzado en la simulación).")

                // La última palabra la tiene el robot: decide con su estado real, aunque la app haya mostrado
                // el botón habilitado. La app puede tener datos viejos; el robot no.
                else -> when (val outcome = simulator.handle(type, missionId, clock())) {
                    is CommandOutcome.Rejected -> resolve(record, CommandStatus.REJECTED, outcome.reason)
                    CommandOutcome.Accepted -> {
                        if (type == CommandType.ACK_ALERT && alert != null) acknowledge(alert.id)
                        if (type in DECISION_COMMANDS) closeDecisionAlerts(type)
                        resolve(record, CommandStatus.CONFIRMED, null)
                    }
                }
            }
        }
    }

    private fun acknowledge(alertId: Long) {
        _alerts.update { list -> list.map { if (it.id == alertId) it.copy(acknowledged = true) else it } }
    }

    /** La orden aceptada es la decisión del operador: las alertas que la esperaban quedan cerradas. */
    private fun closeDecisionAlerts(decision: CommandType) {
        _alerts.update { list ->
            list.map {
                if (it.requiresDecision && !it.acknowledged) {
                    it.copy(acknowledged = true, detail = "${it.detail}\nDecisión del operador: ${decision.label.lowercase()}.")
                } else it
            }
        }
    }

    private fun resolve(record: CommandRecord, status: CommandStatus, reason: String?) {
        val resolved = record.copy(status = status, resolvedAtMs = clock(), reason = reason)
        _commands.update { list -> list.map { if (it.id == record.id) resolved else it } }
        val label = record.type.label + (record.target?.let { " · $it" } ?: "")
        log(
            category = HistoryCategory.COMMAND,
            severity = if (status == CommandStatus.CONFIRMED) AlertSeverity.INFO else AlertSeverity.WARNING,
            title = "$label: ${status.label.lowercase()}",
            detail = reason ?: "El robot aceptó la orden.",
        )
    }

    // ---------- Historial ----------

    // Cada orden, alerta y cambio de conexión se guarda en el historial con su hora, para poder
    // reconstruir después qué pasó en la ronda. Se guarda en el momento en que ocurre, no al cerrar la
    // app: si el sistema mata el proceso, onDestroy puede no ejecutarse y se perdería el registro.
    override fun logOperatorAction(title: String, detail: String) {
        scope.launch { log(HistoryCategory.OPERATOR, AlertSeverity.INFO, title, detail) }
    }

    override fun clearHistory() {
        scope.launch { historyDao.clear() }
    }

    private fun log(category: HistoryCategory, severity: AlertSeverity, title: String, detail: String, at: Long = clock()) {
        val robotName = _selectedRobot.value?.name ?: "—"
        scope.launch {
            historyDao.insert(
                HistoryEventEntity(
                    timestampMs = at,
                    robotName = robotName,
                    category = category.name,
                    severity = severity.name,
                    title = title,
                    detail = detail,
                ),
            )
        }
    }

    private fun clockText(ms: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(ms))

    // ---------- Controles de simulación ----------

    override fun setLinkDown(down: Boolean) {
        _settings.update { it.copy(linkDown = down) }
    }

    override fun setRejectCommands(reject: Boolean) {
        _settings.update { it.copy(rejectCommands = reject) }
    }

    override fun forceLowBattery() {
        scope.launch { sim?.forceLowBattery() }
    }

    override fun forceObstacle() {
        scope.launch { sim?.forceObstacle(clock()) }
    }

    override fun forceIntruder() {
        scope.launch { sim?.forceIntruder(clock()) }
    }

    // Todos los tiempos de la simulación tienen nombre y están en un solo lugar, para ajustarlos sin
    // buscar números sueltos por el código.
    private companion object {
        const val TICK_MS = 1000L
        const val CONNECT_DELAY_MS = 1200L
        const val LINK_TIMEOUT_MS = 3000L
        const val COMMAND_TIMEOUT_MS = 5000L
        const val RESPONSE_MIN_MS = 500L
        const val RESPONSE_MAX_MS = 1100L
        const val MAX_COMMANDS = 30
        val DECISION_COMMANDS = setOf(
            CommandType.RESUME_MISSION,
            CommandType.CANCEL_MISSION,
            CommandType.RETURN_TO_SAFE_POINT,
        )
    }
}
