package ar.edu.uade.robothmi.data.sim

import ar.edu.uade.robothmi.model.Checkpoint
import ar.edu.uade.robothmi.model.MissionPlan
import ar.edu.uade.robothmi.model.RobotAvailability
import ar.edu.uade.robothmi.model.RobotInfo

/** Datos fijos del escenario simulado: flota y rondas disponibles. */
object SimData {
    private const val SITE = "Centro logístico Pilar · Nave 1"

    val robots = listOf(
        RobotInfo("go2-01", "Centinela 01", "Unitree Go2 EDU", SITE, RobotAvailability.AVAILABLE, 86f),
        RobotInfo("go2-02", "Centinela 02", "Unitree Go2 EDU", SITE, RobotAvailability.AVAILABLE, 41f),
        RobotInfo("go2-03", "Centinela 03", "Unitree Go2 EDU", SITE, RobotAvailability.MAINTENANCE, 0f),
    )

    private val access = Checkpoint("cp-access", "Acceso peatonal", 5f, 20f)
    private val dock1 = Checkpoint("cp-dock1", "Muelle 1", 12f, 5f)
    private val dock3 = Checkpoint("cp-dock3", "Muelle 3", 38f, 5f)
    private val eastGate = Checkpoint("cp-east", "Portón este", 55f, 8f)
    private val vault = Checkpoint("cp-vault", "Depósito de alto valor", 55f, 32f)
    private val central = Checkpoint("cp-central", "Pasillo central", 30f, 35f)

    // Esquinas de paso: mantienen el recorrido sobre los pasillos, sin inspección.
    private val cornerNw = Checkpoint("wp-nw", "Esquina noroeste", 5f, 5f)
    private val aisleNorth = Checkpoint("wp-aisle-n", "Pasillo central norte", 30f, 10f)

    val missions = listOf(
        MissionPlan(
            id = "ronda-perimetral",
            name = "Ronda perimetral completa",
            description = "Recorre todo el perímetro interno y verifica los seis puntos de control.",
            checkpoints = listOf(access, dock1, dock3, eastGate, vault, central),
            estimatedSeconds = 115,
            waypoints = listOf(access, cornerNw, dock1, dock3, eastGate, vault, central),
        ),
        MissionPlan(
            id = "ronda-muelles",
            name = "Ronda rápida de muelles",
            description = "Verifica que los muelles de carga estén cerrados y despejados.",
            checkpoints = listOf(dock1, dock3),
            estimatedSeconds = 85,
            waypoints = listOf(cornerNw, dock1, dock3, aisleNorth, central),
        ),
        MissionPlan(
            id = "ronda-alto-valor",
            name = "Verificación de depósito de alto valor",
            description = "Va directo al depósito de alto valor y controla su acceso.",
            checkpoints = listOf(central, vault),
            estimatedSeconds = 75,
        ),
    )
}
