package app.chrapak.data

import android.content.Context
import app.chrapak.health.HealthConnectSource
import app.chrapak.health.NightHealth

/** One night with its events and health data, ready for display in the recordings list. */
data class NightRecordings(
    val night: NightEntity,
    val events: List<SnoreEventEntity>,
    val health: NightHealth,
    val totalSizeBytes: Long,
)

class RecordingsViewModel(
    private val context: Context,
    private val db: ChrapakDatabase,
    private val health: HealthConnectSource,
) {
    private val repo = NightRepository(context, db, health)

    suspend fun all(): List<NightRecordings> {
        val nights = db.nights().all()
        return nights.map { night ->
            val events = db.events().forNight(night.id)
            val healthData = health.readNight(night.sleepStartMs - PAD_MS, endOf(night, events) + PAD_MS)
            val totalSize = events.sumOf { e ->
                repo.clipFile(e)?.length() ?: 0L
            }
            NightRecordings(night, events, healthData, totalSize)
        }
    }

    suspend fun forNight(nightId: String): NightRecordings? {
        val night = db.nights().all().find { it.id == nightId } ?: return null
        val events = db.events().forNight(nightId)
        val healthData = health.readNight(night.sleepStartMs - PAD_MS, endOf(night, events) + PAD_MS)
        val totalSize = events.sumOf { e ->
            repo.clipFile(e)?.length() ?: 0L
        }
        return NightRecordings(night, events, healthData, totalSize)
    }

    fun endOf(night: NightEntity, events: List<SnoreEventEntity>): Long =
        night.sleepEndMs ?: maxOf(night.sleepStartMs, events.maxOfOrNull { it.startMs + it.durationMs } ?: 0L)

    private companion object {
        const val PAD_MS = 30 * 60_000L
    }
}