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

    /** Nights never closed, e.g. because the system killed the service mid-night. */
    @Query("SELECT * FROM nights WHERE sleepEndMs IS NULL")
    suspend fun open(): List<NightEntity>

    /** Newest night first. */
    @Query("SELECT * FROM nights ORDER BY sleepStartMs DESC")
    suspend fun all(): List<NightEntity>
}

@Dao
interface SnoreEventDao {
    @Insert
    suspend fun insert(event: SnoreEventEntity)

    @Query("SELECT COUNT(*) FROM snore_events WHERE nightId = :nightId AND falsePositive = 0")
    suspend fun count(nightId: String): Int

    @Query("SELECT MAX(startMs + durationMs) FROM snore_events WHERE nightId = :nightId")
    suspend fun lastEventEndMs(nightId: String): Long?

    @Query("SELECT * FROM snore_events WHERE nightId = :nightId ORDER BY startMs")
    suspend fun forNight(nightId: String): List<SnoreEventEntity>

    @Query("UPDATE snore_events SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE snore_events SET falsePositive = :falsePositive WHERE id = :id")
    suspend fun setFalsePositive(id: String, falsePositive: Boolean)

    @Query("DELETE FROM snore_events WHERE id = :id")
    suspend fun delete(id: String)
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
