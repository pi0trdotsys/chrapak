package app.chrapak.report

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.SleepStageType
import app.chrapak.health.StageSegment
import app.chrapak.health.VitalPoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Numbers shown at the top of the morning report. Events marked as false positives never count. */
data class NightStats(
    val nightMs: Long,
    val snoringMs: Long,
    val percent: Int,
    val count: Int,
    /** Loudest episode in dBFS of the 100-2000 Hz band; null when there are no episodes. */
    val peakDb: Float?,
    val longest: SnoreEventEntity?,
    val score: Int,
) {
    val snoringMinutes: Int get() = (snoringMs / 60_000.0).roundToInt()
}

object NightReport {
    /** Snoring for this share of the night counts as the worst score, 100. */
    const val FULL_SCORE_SHARE = 0.30

    fun valid(events: List<SnoreEventEntity>) = events.filter { !it.falsePositive }

    fun stats(night: NightEntity, events: List<SnoreEventEntity>): NightStats {
        val valid = valid(events)
        val lastEventEnd = valid.maxOfOrNull { it.startMs + it.durationMs } ?: night.sleepStartMs
        val end = night.sleepEndMs ?: lastEventEnd
        val nightMs = max(1L, max(end, lastEventEnd) - night.sleepStartMs)
        val snoringMs = min(nightMs, valid.sumOf { it.durationMs })
        val share = snoringMs.toDouble() / nightMs
        return NightStats(
            nightMs = nightMs,
            snoringMs = snoringMs,
            percent = (share * 100).roundToInt(),
            count = valid.size,
            peakDb = valid.maxOfOrNull { it.peakDb },
            longest = valid.maxByOrNull { it.durationMs },
            score = min(100, (share / FULL_SCORE_SHARE * 100).roundToInt()),
        )
    }

    fun scoreLabel(score: Int) = when {
        score < 40 -> "Lekkie"
        score < 70 -> "Umiarkowane"
        else -> "Silne"
    }

    fun stageAt(stages: List<StageSegment>, ms: Long): SleepStageType? =
        stages.firstOrNull { ms >= it.startMs && ms < it.endMs }?.stage

    /** Milliseconds of [events] that fall inside each stage. */
    fun snoringMsByStage(events: List<SnoreEventEntity>, stages: List<StageSegment>): Map<SleepStageType, Long> {
        val out = mutableMapOf<SleepStageType, Long>()
        for (segment in stages) {
            for (event in valid(events)) {
                val overlap = min(segment.endMs, event.startMs + event.durationMs) - max(segment.startMs, event.startMs)
                if (overlap > 0) out.merge(segment.stage, overlap, Long::plus)
            }
        }
        return out
    }

    /**
     * How many times more often snoring happens in REM than in other sleep stages, per minute of
     * stage time. Null unless both groups have data, so a missing hypnogram never invents a result.
     */
    fun remRatio(events: List<SnoreEventEntity>, stages: List<StageSegment>): Double? {
        val sleepStages = stages.filter { it.stage != SleepStageType.AWAKE }
        val remMs = sleepStages.filter { it.stage == SleepStageType.REM }.sumOf { it.durationMs }
        val otherMs = sleepStages.filter { it.stage != SleepStageType.REM }.sumOf { it.durationMs }
        if (remMs <= 0 || otherMs <= 0) return null
        val byStage = snoringMsByStage(events, sleepStages)
        val remSnore = byStage[SleepStageType.REM] ?: 0L
        val otherSnore = byStage.filterKeys { it != SleepStageType.REM }.values.sum()
        if (otherSnore <= 0) return null
        return (remSnore.toDouble() / remMs) / (otherSnore.toDouble() / otherMs)
    }

    data class SpO2Dip(val value: Double, val tsMs: Long)

    /** Lowest SpO2 from the start of any episode until [afterMs] after it ends; null without samples. */
    fun lowestSpo2NearEvents(
        events: List<SnoreEventEntity>,
        spo2: List<VitalPoint>,
        afterMs: Long = 90_000,
    ): SpO2Dip? {
        val windows = valid(events).map { it.startMs to it.startMs + it.durationMs + afterMs }
        return spo2
            .filter { p -> windows.any { (from, to) -> p.tsMs in from..to } }
            .minByOrNull { it.value }
            ?.let { SpO2Dip(it.value, it.tsMs) }
    }

    /** Lowest SpO2 of the whole night, to compare with the value near episodes. */
    fun lowestSpo2(spo2: List<VitalPoint>): SpO2Dip? =
        spo2.minByOrNull { it.value }?.let { SpO2Dip(it.value, it.tsMs) }
}
