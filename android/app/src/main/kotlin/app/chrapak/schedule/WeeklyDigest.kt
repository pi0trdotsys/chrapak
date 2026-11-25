package app.chrapak.schedule

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import app.chrapak.data.isNap
import app.chrapak.data.isSnoring
import app.chrapak.report.NightReport
import app.chrapak.report.NightStats
import app.chrapak.ui.plural
import kotlin.math.roundToInt

/** What the week's summary notification wants to say. */
data class WeeklyDigest(
    val nightCount: Int,
    val averageScore: Int?,
    /** Nights whose score went up compared to the night before — for the "worse than usual" wording. */
    val worseThanPrevCount: Int,
    /** When there are no nights, the whole notification is skipped. */
    val isEmpty: Boolean = false,
    /** Average score of the 7 days before, for a "better / worse than last week" note; null when unknown. */
    val previousAverageScore: Int? = null,
    /** The worst scored night of the week: its start and score. Null when no night has a score. */
    val worstStartMs: Long? = null,
    val worstScore: Int? = null,
)

/**
 * Builds a one-line weekly digest. Empty when the week has no nights worth talking about, so the
 * caller can decide to drop the notification entirely.
 */
object WeeklyDigestBuilder {
    /**
     * Nights are expected newest-last. "This week" means the last [lookbackDays] days, ending at
     * the start of [nowMs]'s day. Nights still open (no end) are skipped: a score in progress
     * means nothing to compare against. [zone] is a parameter so tests are not at the mercy of the
     * machine's timezone.
     */
    fun build(
        nights: List<NightEntity>,
        eventsByNight: Map<String, List<SnoreEventEntity>>,
        nowMs: Long,
        lookbackDays: Int = 7,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): WeeklyDigest {
        if (nights.isEmpty()) return WeeklyDigest(0, null, 0, isEmpty = true)
        val cutoff = startOfDay(nowMs, zone) - (lookbackDays - 1) * DAY_MS
        val previousCutoff = cutoff - lookbackDays * DAY_MS
        // Naps are not nights: a 20-minute nap would drag the week's average around.
        val closed = nights.filter { it.sleepEndMs != null && it.sleepStartMs >= cutoff && !it.isNap() }
        if (closed.isEmpty()) return WeeklyDigest(0, null, 0, isEmpty = true)
        fun scored(list: List<NightEntity>) = list.map { night ->
            // The week has already ended: use the night end as the reference, not now.
            night to NightReport.stats(night, eventsByNight[night.id].orEmpty(), nowMs = night.sleepEndMs!!)
        }.filter { !it.second.tooShort }
        val scored = scored(closed)
        val avg = average(scored.map { it.second.score })
        // A night counts as "worse" if it scored strictly more than the night before it, in the same week.
        val worse = scored.map { it.second }.zipWithNext().count { (prev, curr) -> curr.score > prev.score }
        val previous = scored(nights.filter { it.sleepEndMs != null && !it.isNap() && it.sleepStartMs in previousCutoff until cutoff })
        val worst = scored.maxByOrNull { it.second.score }
        return WeeklyDigest(
            nightCount = closed.size,
            averageScore = avg,
            worseThanPrevCount = worse,
            previousAverageScore = average(previous.map { it.second.score }),
            worstStartMs = worst?.first?.sleepStartMs,
            worstScore = worst?.second?.score,
        )
    }

    /**
     * Human line for the notification, never empty when the digest is not empty: the nights, the
     * average against the week before, and the worst night to look at.
     */
    fun line(digest: WeeklyDigest, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (digest.isEmpty || digest.nightCount == 0) return ""
        val nights = "${digest.nightCount} ${plural(digest.nightCount, "noc", "noce", "nocy")}"
        val avg = digest.averageScore ?: return "$nights · za krótkie na wynik"
        val trend = digest.previousAverageScore?.let { prev ->
            val word = when {
                avg <= prev - 10 -> "lepiej niż"
                avg >= prev + 10 -> "gorzej niż"
                else -> "podobnie jak"
            }
            " ($word tydzień wcześniej: $prev/100)"
        } ?: ""
        val worst = if (digest.worstStartMs != null && digest.worstScore != null && digest.nightCount > 1)
            " · najgorsza noc: ${dayLabel(digest.worstStartMs, zone)}, ${digest.worstScore}/100" else ""
        return "$nights · średni wynik $avg/100$trend$worst"
    }

    private fun average(scores: List<Int>): Int? =
        if (scores.isEmpty()) null else (scores.sum().toDouble() / scores.size).roundToInt()

    private val dayNames = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")

    /** The evening a night began on: a night started at 1:00 on Tuesday belongs to Monday. */
    private fun dayLabel(startMs: Long, zone: java.time.ZoneId): String {
        val local = java.time.Instant.ofEpochMilli(startMs).atZone(zone)
        val day = if (local.hour < 12) local.minusDays(1) else local
        return dayNames[day.dayOfWeek.value - 1]
    }

    private const val DAY_MS = 24 * 60 * 60_000L

    private fun startOfDay(ms: Long, zone: java.time.ZoneId): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}