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

/**
 * Whether an episode counts as snoring in the report. Every episode the gate catches is saved with
 * its audio, so a missed snore can still be found and replayed, but only those the classifier is
 * reasonably sure about count. Ones marked as a false alarm never do.
 */
val SnoreEventEntity.isSnoring: Boolean
    get() = !falsePositive && confidence >= app.chrapak.detection.DetectionConfig.MIN_CONFIDENCE

/** Saved, but the classifier was not sure it was snoring: worth a listen, not counted. */
val SnoreEventEntity.isUncertain: Boolean
    get() = !falsePositive && confidence < app.chrapak.detection.DetectionConfig.MIN_CONFIDENCE
