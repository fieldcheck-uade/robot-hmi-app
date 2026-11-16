package ar.edu.uade.robothmi.model

import kotlin.math.hypot

/**
 * Plano esquemático de la nave logística (en metros, origen arriba a la izquierda).
 * Lo comparten el simulador (para moverse) y la UI (para dibujar el mapa).
 */
object SiteMap {
    const val WIDTH = 60f
    const val HEIGHT = 40f

    const val BASE_X = 5f
    const val BASE_Y = 35f
    const val SAFE_POINT_NAME = "Base de carga"

    data class Zone(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    }

    val zones = listOf(
        Zone("Muelles de carga", 0f, 0f, 60f, 10f),
        Zone("Estanterías A", 10f, 12f, 28f, 28f),
        Zone("Estanterías B", 32f, 12f, 48f, 28f),
        Zone("Depósito de alto valor", 48f, 26f, 60f, 40f),
        Zone("Base de carga", 0f, 30f, 10f, 40f),
    )

    fun zoneAt(x: Float, y: Float): String =
        zones.firstOrNull { it.contains(x, y) }?.name ?: "Pasillo perimetral"

    fun distanceToBase(x: Float, y: Float): Float = hypot(x - BASE_X, y - BASE_Y)
}
