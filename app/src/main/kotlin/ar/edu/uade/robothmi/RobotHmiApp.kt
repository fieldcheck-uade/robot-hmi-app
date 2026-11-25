package ar.edu.uade.robothmi

import android.app.Application
import android.content.Context
import androidx.room.Room
import ar.edu.uade.robothmi.data.RobotRepository
import ar.edu.uade.robothmi.data.SimulationControls
import ar.edu.uade.robothmi.data.local.HistoryDatabase
import ar.edu.uade.robothmi.data.local.ThemeSettings
import ar.edu.uade.robothmi.data.sim.SimulatedRobotRepository

/**
 * Inyección de dependencias manual: el único lugar que conoce la implementación simulada.
 *
 * Acá se crean una sola vez los objetos que comparte toda la app (la base de datos y el Repository)
 * y se decide qué implementación se usa. Lo hacemos en un único lugar para que ni las pantallas ni
 * el ViewModel creen sus propias dependencias: el día que se conecte un robot real, se cambia
 * `SimulatedRobotRepository` por la implementación real en este archivo y el resto de la app no se toca.
 * Es lo mismo que haría una librería como Hilt, pero escrito a mano porque la app es chica.
 */
class AppContainer(context: Context) {
    // Creamos UNA sola base de datos para toda la app. Abrir una por pantalla sería caro y podría
    // dejar datos inconsistentes. El archivo "history.db" queda en el almacenamiento privado de la app:
    // sobrevive a cerrar la app y a que el sistema la mate, y solo se borra al desinstalar.
    private val database = Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()

    // Una sola instancia del repositorio simulado que implementa las DOS interfaces.
    private val simulated = SimulatedRobotRepository(database.historyDao())

    // Hacia afuera exponemos las interfaces, no la clase concreta: el ViewModel recibe un
    // RobotRepository y no sabe (ni le importa) que el robot es simulado.
    val repository: RobotRepository = simulated
    val simulationControls: SimulationControls = simulated
    val themeSettings = ThemeSettings(context)
}

/**
 * Clase Application (declarada en el Manifest con android:name=".RobotHmiApp").
 *
 * Vive lo mismo que el proceso de la app, por eso es el lugar natural para guardar el contenedor.
 * Le pasamos `this` (el Context de la Application) y no el de una Activity: si el contenedor, que vive
 * mucho, guardara una Activity, esa Activity no se podría liberar al rotar y tendríamos una fuga de memoria.
 * `by lazy` hace que se cree recién la primera vez que alguien lo pide (al construir el ViewModel).
 */
class RobotHmiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
