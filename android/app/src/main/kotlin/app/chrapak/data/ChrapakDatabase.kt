package app.chrapak.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface NightDao {
    @Insert
    suspend fun insert(night: NightEntity)

    @Query("UPDATE nights SET sleepEndMs = :endMs WHERE id = :id")
    suspend fun finish(id: String, endMs: Long)
}

@Dao
interface SnoreEventDao {
    @Insert
    suspend fun insert(event: SnoreEventEntity)

    @Query("SELECT COUNT(*) FROM snore_events WHERE nightId = :nightId AND falsePositive = 0")
    suspend fun count(nightId: String): Int
}

@Database(entities = [NightEntity::class, SnoreEventEntity::class], version = 1, exportSchema = true)
abstract class ChrapakDatabase : RoomDatabase() {
    abstract fun nights(): NightDao
    abstract fun events(): SnoreEventDao

    companion object {
        @Volatile
        private var instance: ChrapakDatabase? = null

        fun get(context: Context): ChrapakDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChrapakDatabase::class.java,
                "chrapak.db",
            ).build().also { instance = it }
        }
    }
}
