package ar.edu.uade.robothmi

import ar.edu.uade.robothmi.data.sim.CommandOutcome
import ar.edu.uade.robothmi.data.sim.RobotSimulator
import ar.edu.uade.robothmi.data.sim.SimData
import ar.edu.uade.robothmi.data.sim.SimEvent
import ar.edu.uade.robothmi.domain.Availability
import ar.edu.uade.robothmi.domain.CommandRules
import ar.edu.uade.robothmi.model.AlertType
import ar.edu.uade.robothmi.model.CommandType
import ar.edu.uade.robothmi.model.ConnectionState
import ar.edu.uade.robothmi.model.MissionState
import ar.edu.uade.robothmi.model.OperationalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios del robot simulado y de las reglas de órdenes.
 *
 * Como RobotSimulator y CommandRules son Kotlin puro, los probamos directo en la JVM: sin emulador,
 * sin Android y sin corrutinas. El tiempo lo avanzamos a mano (`now` + `run(segundos)`), así una ronda
 * de dos minutos se prueba en milisegundos y el resultado es siempre el mismo.
 * Cada test sigue la estructura Given / When / Then: dado un robot en cierto estado, cuando recibe una
 * orden o pasa el tiempo, entonces se verifica en qué estado quedó o por qué rechazó la orden.
 */
class RobotSimulatorTest {
    private var now = 0L
    private fun newSim(robotIndex: Int = 0) = RobotSimulator(SimData.robots[robotIndex], SimData.missions)

    private fun RobotSimulator.run(seconds: Int) = repeat(seconds) {
        now += 1000
        tick(1f, now)
    }

    private fun RobotSimulator.send(type: CommandType, missionId: String? = null) = handle(type, missionId, now)

    private fun RobotSimulator.alertTypes() =
        drainEvents().filterIsInstance<SimEvent.AlertRaised>().map { it.type }

    @Test
    fun `mision normal inicia progresa y termina en la base`() {
        val sim = newSim()
        assertEquals(CommandOutcome.Accepted, sim.send(CommandType.START_MISSION, "ronda-muelles"))
        sim.run(10)
        val mid = sim.snapshot(now)
        assertEquals(OperationalState.PATROLLING, mid.opState)
        assertTrue(mid.mission!!.progress in 0.05f..0.95f)

        sim.run(120)
        val end = sim.snapshot(now)
        assertEquals(MissionState.COMPLETED, end.mission!!.state)
        assertEquals(1f, end.mission!!.progress, 0.001f)
        assertEquals(2, end.mission!!.checkpointsDone)
        assertEquals(OperationalState.IDLE, end.opState)
        assertTrue(end.atSafePoint)
    }

    @Test
    fun `bateria baja emite una advertencia una sola vez`() {
        val sim = newSim()
        sim.send(CommandType.START_MISSION, "ronda-perimetral")
        sim.run(5)
        sim.drainEvents()
        sim.forceLowBattery()
        sim.run(3)
        assertEquals(listOf(AlertType.LOW_BATTERY), sim.alertTypes())
        // Sigue patrullando: la decisión de regresar es del operador.
        assertEquals(OperationalState.PATROLLING, sim.state)
        assertEquals(CommandOutcome.Accepted, sim.send(CommandType.RETURN_TO_SAFE_POINT))
        assertEquals(OperationalState.RETURNING, sim.state)
        assertEquals(MissionState.ABORTED, sim.snapshot(now).mission!!.state)
    }

    @Test
    fun `el robot rechaza iniciar con bateria insuficiente`() {
        val sim = newSim()
        sim.send(CommandType.START_MISSION, "ronda-muelles")
        sim.run(5)
        sim.forceLowBattery()
        sim.send(CommandType.RETURN_TO_SAFE_POINT)
        sim.run(8) // llega a la base con menos de 25 %
        assertTrue(sim.snapshot(now).atSafePoint)
        assertTrue(sim.send(CommandType.START_MISSION, "ronda-muelles") is CommandOutcome.Rejected)
    }

    @Test
    fun `obstaculo deja al robot esperando decision y se puede reanudar`() {
        val sim = newSim()
        sim.send(CommandType.START_MISSION, "ronda-perimetral")
        sim.run(5)
        sim.drainEvents()
        assertTrue(sim.forceObstacle(now))
        assertEquals(OperationalState.WAITING_OPERATOR, sim.state)
        assertEquals(listOf(AlertType.OBSTACLE), sim.alertTypes())

        val before = sim.snapshot(now)
        sim.run(5)
        val after = sim.snapshot(now)
        assertEquals(before.x, after.x, 0.001f)
        assertEquals(MissionState.PAUSED, after.mission!!.state)

        assertEquals(CommandOutcome.Accepted, sim.send(CommandType.RESUME_MISSION))
        assertEquals(OperationalState.PATROLLING, sim.state)
    }

    @Test
    fun `persona detectada suma una anomalia y no aplica si no patrulla`() {
        val sim = newSim()
        assertFalse(sim.forceIntruder(now))
        sim.send(CommandType.START_MISSION, "ronda-alto-valor")
        sim.run(5)
        assertTrue(sim.forceIntruder(now))
        assertEquals(1, sim.snapshot(now).anomaliesDetected)
        assertEquals(OperationalState.WAITING_OPERATOR, sim.state)
    }

    @Test
    fun `detencion operativa pausa la mision y se rechaza si ya esta detenido`() {
        val sim = newSim()
        assertTrue(sim.send(CommandType.OPERATIONAL_STOP) is CommandOutcome.Rejected)
        sim.send(CommandType.START_MISSION, "ronda-muelles")
        sim.run(3)
        assertEquals(CommandOutcome.Accepted, sim.send(CommandType.OPERATIONAL_STOP))
        assertEquals(OperationalState.STOPPED, sim.state)
        assertEquals(MissionState.PAUSED, sim.snapshot(now).mission!!.state)
        assertTrue(sim.send(CommandType.OPERATIONAL_STOP) is CommandOutcome.Rejected)
    }

    @Test
    fun `sin enlace el robot pausa la ronda y espera decision`() {
        val sim = newSim()
        sim.send(CommandType.START_MISSION, "ronda-perimetral")
        sim.run(5)
        sim.drainEvents()

        sim.setOperatorLink(false)
        sim.run(RobotSimulator.LINK_LOSS_PAUSE_S.toInt() - 1)
        assertEquals(OperationalState.PATROLLING, sim.state) // un corte breve no interrumpe la ronda

        sim.run(1)
        assertEquals(OperationalState.WAITING_OPERATOR, sim.state)
        assertEquals(listOf(AlertType.UNSUPERVISED_PAUSE), sim.alertTypes())
        val stopped = sim.snapshot(now)
        sim.run(20)
        assertEquals(stopped.x, sim.snapshot(now).x, 0.001f)
        assertEquals(MissionState.PAUSED, sim.snapshot(now).mission!!.state)
        assertTrue(sim.alertTypes().isEmpty()) // avisa una sola vez

        sim.setOperatorLink(true)
        assertEquals(CommandOutcome.Accepted, sim.send(CommandType.RESUME_MISSION))
        assertEquals(OperationalState.PATROLLING, sim.state)
    }

    @Test
    fun `sin enlace el regreso a base no se interrumpe y la bateria critica lo trae de vuelta`() {
        val returning = newSim()
        returning.send(CommandType.START_MISSION, "ronda-perimetral")
        returning.run(8)
        returning.send(CommandType.RETURN_TO_SAFE_POINT)
        returning.setOperatorLink(false)
        returning.run(RobotSimulator.LINK_LOSS_PAUSE_S.toInt() + 2)
        assertTrue(returning.state == OperationalState.RETURNING || returning.snapshot(now).atSafePoint)

        val paused = newSim()
        paused.send(CommandType.START_MISSION, "ronda-perimetral")
        paused.run(8)
        paused.setOperatorLink(false)
        paused.run(RobotSimulator.LINK_LOSS_PAUSE_S.toInt())
        assertEquals(OperationalState.WAITING_OPERATOR, paused.state)
        paused.forceLowBattery()
        repeat(1000) { if (paused.state != OperationalState.RETURNING) paused.run(1) }
        assertEquals(OperationalState.RETURNING, paused.state)
    }

    @Test
    fun `reglas de habilitacion de la interfaz`() {
        val sim = newSim()
        val atBase = sim.snapshot(now)
        fun rule(type: CommandType, connection: ConnectionState = ConnectionState.CONNECTED, t: ar.edu.uade.robothmi.model.Telemetry? = atBase, pending: Set<CommandType> = emptySet()) =
            CommandRules.availability(type, connection, t, pending)

        assertTrue(rule(CommandType.START_MISSION).isEnabled)
        assertFalse(rule(CommandType.PAUSE_MISSION).isEnabled)
        assertFalse(rule(CommandType.RETURN_TO_SAFE_POINT).isEnabled)
        assertFalse(rule(CommandType.OPERATIONAL_STOP).isEnabled)
        assertFalse(rule(CommandType.START_MISSION, pending = setOf(CommandType.START_MISSION)).isEnabled)
        assertFalse(rule(CommandType.START_MISSION, t = null).isEnabled)

        sim.send(CommandType.START_MISSION, "ronda-muelles")
        sim.run(5)
        val patrolling = sim.snapshot(now)
        assertTrue(rule(CommandType.PAUSE_MISSION, t = patrolling).isEnabled)
        assertTrue(rule(CommandType.CANCEL_MISSION, t = patrolling).isEnabled)
        assertFalse(rule(CommandType.START_MISSION, t = patrolling).isEnabled)

        // Sin enlace solo quedan las órdenes de seguridad.
        assertFalse(rule(CommandType.PAUSE_MISSION, ConnectionState.LOST, patrolling).isEnabled)
        assertTrue(rule(CommandType.OPERATIONAL_STOP, ConnectionState.LOST, patrolling).isEnabled)
        assertTrue(rule(CommandType.RETURN_TO_SAFE_POINT, ConnectionState.LOST, patrolling).isEnabled)
        assertTrue(rule(CommandType.OPERATIONAL_STOP, ConnectionState.LOST, atBase) is Availability.Enabled)
    }
}
