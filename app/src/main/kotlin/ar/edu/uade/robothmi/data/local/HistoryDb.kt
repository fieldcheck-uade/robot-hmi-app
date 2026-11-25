package ar.edu.uade.robothmi.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Persistencia local del historial con Room.
 *
 * Usamos Room (una capa sobre SQLite, la base de datos que ya trae Android) para guardar el historial
 * de la ronda: conexiones, órdenes, alertas y misiones. El camino de los datos es
 * UI → ViewModel → Repository → DAO → SQLite; la UI nunca ve este archivo ni escribe SQL.
 *
 * Elegimos Room y no un archivo de texto porque el historial son registros que se ordenan por fecha,
 * se limitan a los últimos 500 y se borran, y Room hace eso con consultas que se verifican al compilar.
 *
 * Es lo único de la app que sobrevive si se cierra la app o si el sistema la mata: la sesión con el
 * robot (telemetría, alertas, órdenes) vive en memoria en el Repository y se pierde. Para un sistema
 * de vigilancia es importante que el registro de lo que pasó no dependa de que la app siga abierta.
 */

/**
 * Tabla de eventos del historial: cada instancia de esta clase es una fila.
 * `id = 0` con autoGenerate significa "que lo genere SQLite" al insertar.
 * Solo guardamos datos, nada de UI (colores, textos ya formateados).
 * `category` y `severity` se guardan como texto (el `name` del enum). Al leerlos, el Repository los
 * convierte de vuelta con runCatching: si una fila tuviera un valor que ya no existe en el enum,
 * la app usa un valor por defecto en lugar de crashear.
 */
@Entity(tableName = "history_events")
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val robotName: String,
    val category: String,
    val severity: String,
    val title: String,
    val detail: String,
)

/**
 * Operaciones sobre el historial. Acá solo las declaramos: el código que las implementa lo genera
 * Room al compilar (con KSP, configurado en build.gradle.kts).
 */
@Dao
interface HistoryDao {
    // Devuelve Flow para que la pantalla de Historial se actualice sola: cada vez que se inserta o se
    // borra un evento, Room vuelve a ejecutar la consulta y emite la lista nueva.
    // Ordenamos y limitamos en SQL (ORDER BY ... LIMIT 500) en vez de traer toda la tabla y filtrar en
    // Kotlin, así la consulta no crece sin límite aunque el historial tenga miles de eventos.
    @Query("SELECT * FROM history_events ORDER BY timestampMs DESC, id DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEventEntity>>

    // `suspend`: escribir en disco no bloquea el hilo de la UI (Room usa su propio hilo).
    // Es importante porque se registra un evento por cada orden y alerta mientras el operador
    // está usando la pantalla.
    @Insert
    suspend fun insert(event: HistoryEventEntity)

    @Query("DELETE FROM history_events")
    suspend fun clear()
}

/**
 * La base de datos: se crea una sola vez, en AppContainer.
 * `version = 1` identifica la estructura de las tablas. Si más adelante se agrega una columna, hay que
 * subir la versión y escribir una migración; si no, los usuarios que ya tienen la app perderían su historial.
 * `exportSchema = false`: no guardamos el esquema en JSON. Si la base empieza a cambiar, conviene activarlo
 * para poder probar las migraciones.
 */
@Database(entities = [HistoryEventEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
