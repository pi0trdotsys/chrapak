package app.chrapak.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A daily listening window given as minutes after midnight. It may cross midnight (23:00 to 07:00).
 * Pure functions so the tricky cases (midnight, equal times) are tested without a phone.
 */
object ScheduleMath {
    const val MINUTES_PER_DAY = 24 * 60

    /** Whether [minuteOfDay] falls inside the window. An empty window (start == end) is never active. */
    fun isInWindow(minuteOfDay: Int, startMin: Int, endMin: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> minuteOfDay in startMin until endMin
        else -> minuteOfDay >= startMin || minuteOfDay < endMin
    }

    fun minuteOfDay(nowMs: Long, zone: ZoneId): Int {
        val time = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /** The next moment strictly after [nowMs] at which the clock shows [minuteOfDay]. */
    fun nextOccurrenceMs(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var date: LocalDate = now.toLocalDate()
        var candidate = date.atTime(time).atZone(zone)
        if (!candidate.toInstant().isAfter(now.toInstant())) {
            date = date.plusDays(1)
            candidate = date.atTime(time).atZone(zone)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun format(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
