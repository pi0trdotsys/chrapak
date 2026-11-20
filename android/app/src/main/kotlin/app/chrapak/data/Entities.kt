package app.chrapak.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mirrors the `Night` type of the web prototype (`src/data/mock.ts`). */
@Entity(tableName = "nights")
data class NightEntity(
    @PrimaryKey val id: String,
    val sleepStartMs: Long,
    /** Null while the night is still being recorded. */
    val sleepEndMs: Long?,
    /** Comma-separated tag names; empty when none. */
    val tags: String = "",
    /** Computed after the night from stored events; null until then. */
    val snoreScore: Int? = null,
)

/** Mirrors the `SnoreEvent` type of the web prototype. Levels are dBFS, see `DetectedEvent`. */
@Entity(
    tableName = "snore_events",
    foreignKeys = [
        ForeignKey(
            entity = NightEntity::class,
            parentColumns = ["id"],
            childColumns = ["nightId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("nightId")],
)
data class SnoreEventEntity(
    @PrimaryKey val id: String,
    val nightId: String,
    val startMs: Long,
    val durationMs: Long,
    val peakDb: Float,
    val avgDb: Float,
    val confidence: Float,
    val clipPath: String?,
    val starred: Boolean = false,
    val falsePositive: Boolean = false,
)
