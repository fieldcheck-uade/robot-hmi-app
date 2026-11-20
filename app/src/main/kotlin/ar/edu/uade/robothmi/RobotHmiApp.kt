package ar.edu.uade.robothmi

import android.app.Application
import android.content.Context
import androidx.room.Room
import ar.edu.uade.robothmi.data.RobotRepository
import ar.edu.uade.robothmi.data.SimulationControls
import ar.edu.uade.robothmi.data.local.HistoryDatabase
import ar.edu.uade.robothmi.data.local.ThemeSettings
import ar.edu.uade.robothmi.data.sim.SimulatedRobotRepository

/** Inyección de dependencias manual: el único lugar que conoce la implementación simulada. */
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()
    private val simulated = SimulatedRobotRepository(database.historyDao())

    val repository: RobotRepository = simulated
    val simulationControls: SimulationControls = simulated
    val themeSettings = ThemeSettings(context)
}

class RobotHmiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
