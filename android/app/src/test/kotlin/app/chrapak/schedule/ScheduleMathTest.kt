package app.chrapak.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleMathTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun at(h: Int, m: Int, day: Int = 20) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private val night = 23 * 60 to 7 * 60

    @Test
    fun `a window crossing midnight is active on both sides of it`() {
        assertTrue(ScheduleMath.isInWindow(23 * 60, night.first, night.second))
        assertTrue(ScheduleMath.isInWindow(0, night.first, night.second))
        assertTrue(ScheduleMath.isInWindow(6 * 60 + 59, night.first, night.second))
        assertFalse(ScheduleMath.isInWindow(7 * 60, night.first, night.second))
        assertFalse(ScheduleMath.isInWindow(12 * 60, night.first, night.second))
        assertFalse(ScheduleMath.isInWindow(22 * 60 + 59, night.first, night.second))
    }

    @Test
    fun `a same-day window and an empty window`() {
        assertTrue(ScheduleMath.isInWindow(13 * 60, 13 * 60, 15 * 60))
        assertFalse(ScheduleMath.isInWindow(15 * 60, 13 * 60, 15 * 60))
        assertFalse(ScheduleMath.isInWindow(10 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun `next occurrence is later today or tomorrow, never now`() {
        assertEquals(at(23, 0), ScheduleMath.nextOccurrenceMs(at(15, 0), 23 * 60, zone))
        assertEquals(at(7, 0, day = 21), ScheduleMath.nextOccurrenceMs(at(15, 0), 7 * 60, zone))
        // Exactly at the time: the next one is tomorrow, otherwise an alarm would fire in a loop.
        assertEquals(at(23, 0, day = 21), ScheduleMath.nextOccurrenceMs(at(23, 0), 23 * 60, zone))
    }

    @Test
    fun `next occurrence survives the autumn clock change`() {
        // 2026-10-25 is the day Poland moves from CEST to CET, so that day has 25 hours.
        val before = LocalDateTime.of(2026, 10, 24, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val next = ScheduleMath.nextOccurrenceMs(before, 7 * 60, zone)
        assertEquals(LocalDateTime.of(2026, 10, 25, 7, 0).atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `formats and reads the minute of day`() {
        assertEquals("07:05", ScheduleMath.format(7 * 60 + 5))
        assertEquals(23 * 60 + 15, ScheduleMath.minuteOfDay(at(23, 15), zone))
    }

    // 2026-03-01 is a Sunday.
    private fun sundayAt(h: Int, m: Int) = LocalDateTime.of(2026, 3, 1, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun nextSundayAt(h: Int, m: Int) = LocalDateTime.of(2026, 3, 8, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a weekly occurrence lands on the next chosen day of week`() {
        val wednesday = LocalDateTime.of(2026, 2, 25, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(sundayAt(10, 0), ScheduleMath.nextWeeklyOccurrenceMs(wednesday, 10 * 60, 7, zone))
    }

    @Test
    fun `a weekly occurrence later today is today, not in a week`() {
        assertEquals(sundayAt(10, 0), ScheduleMath.nextWeeklyOccurrenceMs(sundayAt(9, 0), 10 * 60, 7, zone))
    }

    @Test
    fun `a weekly occurrence exactly now or already past moves to the next week`() {
        assertEquals(nextSundayAt(10, 0), ScheduleMath.nextWeeklyOccurrenceMs(sundayAt(10, 0), 10 * 60, 7, zone))
        assertEquals(nextSundayAt(10, 0), ScheduleMath.nextWeeklyOccurrenceMs(sundayAt(11, 0), 10 * 60, 7, zone))
    }
}
