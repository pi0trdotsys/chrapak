package app.chrapak.schedule

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
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
        val closed = nights.filter { it.sleepEndMs != null && it.sleepStartMs >= cutoff }
        if (closed.isEmpty()) return WeeklyDigest(0, null, 0, isEmpty = true)
        val stats = closed.map { night ->
            val events = eventsByNight[night.id].orEmpty()
            // The week has already ended: use the night end as the reference, not now.
            NightReport.stats(night, events, nowMs = night.sleepEndMs!!)
        }
        val scored = stats.filter { !it.tooShort }
        val avg = if (scored.isNotEmpty()) (scored.sumOf { it.score }.toDouble() / scored.size).roundToInt() else null
        // A night counts as "worse" if it scored strictly more than the night before it, in the same week.
        val worse = scored.zipWithNext().count { (prev, curr) -> curr.score > prev.score }
        return WeeklyDigest(nightCount = closed.size, averageScore = avg, worseThanPrevCount = worse)
    }

    /** Human line for the notification, never empty when the digest is not empty. */
    fun line(digest: WeeklyDigest): String {
        if (digest.isEmpty || digest.nightCount == 0) return ""
        val nights = "${digest.nightCount} ${plural(digest.nightCount, "noc", "noce", "nocy")}"
        val score = digest.averageScore?.let { " · średni wynik $it/100" } ?: ""
        val worse = if (digest.worseThanPrevCount > 0)
            " · ${digest.worseThanPrevCount} ${plural(digest.worseThanPrevCount, "noc gorsza", "noce gorsze", "nocy gorszych")} od poprzedniej" else ""
        return "$nights$score$worse"
    }

    private const val DAY_MS = 24 * 60 * 60_000L

    private fun startOfDay(ms: Long, zone: java.time.ZoneId): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}