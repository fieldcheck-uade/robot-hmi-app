package ar.edu.uade.robothmi.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_events ORDER BY timestampMs DESC, id DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEventEntity>>

    @Insert
    suspend fun insert(event: HistoryEventEntity)

    @Query("DELETE FROM history_events")
    suspend fun clear()
}

@Database(entities = [HistoryEventEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
