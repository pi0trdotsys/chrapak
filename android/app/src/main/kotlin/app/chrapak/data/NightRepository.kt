package app.chrapak.data

import android.content.Context
import app.chrapak.health.HealthConnectSource
import app.chrapak.health.NightHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the report and clip list need for one night: our events plus whatever Health Connect has. */
class NightRepository(
    private val context: Context,
    private val db: ChrapakDatabase,
    private val health: HealthConnectSource,
) {
    suspend fun nights(): List<NightEntity> = db.nights().all()

    suspend fun events(nightId: String): List<SnoreEventEntity> = db.events().forNight(nightId)

    /** End of a night; one still being recorded ends at its last event or now. */
    fun endOf(night: NightEntity, events: List<SnoreEventEntity>): Long =
        night.sleepEndMs ?: maxOf(night.sleepStartMs, events.maxOfOrNull { it.startMs + it.durationMs } ?: 0L)

    /** Health data for the night window, padded a little because a ring may log slightly earlier or later. */
    suspend fun health(night: NightEntity, events: List<SnoreEventEntity>): NightHealth =
        health.readNight(night.sleepStartMs - PAD_MS, endOf(night, events) + PAD_MS)

    suspend fun healthConnected(): Boolean = health.hasPermissions()

    /** Sleep, SpO2 and heart rate for an arbitrary window; the settings preview uses the last 24 hours. */
    suspend fun healthBetween(fromMs: Long, toMs: Long): NightHealth = health.readNight(fromMs, toMs)

    fun clipFile(event: SnoreEventEntity): File? =
        event.clipPath?.let { File(context.filesDir, it) }?.takeIf { it.exists() }

    suspend fun setStarred(event: SnoreEventEntity, starred: Boolean) = db.events().setStarred(event.id, starred)

    suspend fun setFalsePositive(event: SnoreEventEntity, value: Boolean) = db.events().setFalsePositive(event.id, value)

    suspend fun delete(event: SnoreEventEntity) {
        event.clipPath?.let { File(context.filesDir, it).delete() }
        db.events().delete(event.id)
    }

    /** Every night with its episodes and the space its clips take, newest first, for the Recordings tab. */
    suspend fun recordings(): List<NightRecordings> = withContext(Dispatchers.IO) {
        db.nights().all().map { night ->
            val events = db.events().forNight(night.id)
            NightRecordings(night, events, events.sumOf { clipFile(it)?.length() ?: 0L })
        }
    }

    private companion object {
        const val PAD_MS = 30 * 60_000L
    }
}

/** One night as the Recordings tab lists it. */
data class NightRecordings(val night: NightEntity, val events: List<SnoreEventEntity>, val clipBytes: Long) {
    val snoring: Int get() = events.count { it.isSnoring }
    val uncertain: Int get() = events.count { it.isUncertain }
}
