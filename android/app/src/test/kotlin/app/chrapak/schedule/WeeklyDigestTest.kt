package app.chrapak.schedule

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyDigestTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val sunday: LocalDate = LocalDate.of(2024, 3, 10)
    private val now: Long = sunday.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    /** A closed night [daysAgo] days before [sunday], 8 h long, with [snoreMin] minutes of snoring. */
    private fun night(daysAgo: Long, snoreMin: Long, confidence: Float = 0.9f): Pair<NightEntity, List<SnoreEventEntity>> {
        val day = sunday.minusDays(daysAgo)
        val start = day.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val end = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val id = "n$daysAgo"
        val events = if (snoreMin > 0) {
            listOf(SnoreEventEntity("$id-e", id, start + 3_600_000, snoreMin * 60_000, -40f, -50f, confidence, null))
        } else {
            emptyList()
        }
        return NightEntity(id, start, end) to events
    }

    private fun build(vararg nights: Pair<NightEntity, List<SnoreEventEntity>>, lookbackDays: Int = 7) =
        WeeklyDigestBuilder.build(nights.map { it.first }, nights.associate { it.first.id to it.second }, now, lookbackDays, zone)

    @Test
    fun `an empty week says nothing worth a notification`() {
        val digest = WeeklyDigestBuilder.build(emptyList(), emptyMap(), now, zone = zone)
        assertTrue(digest.isEmpty)
        assertEquals(0, digest.nightCount)
        assertEquals("", WeeklyDigestBuilder.line(digest))
    }

    @Test
    fun `a night still in progress is skipped`() {
        val open = NightEntity("open", now - 3_600_000, null)
        val digest = WeeklyDigestBuilder.build(listOf(open), emptyMap(), now, zone = zone)
        assertTrue(digest.isEmpty)
    }

    @Test
    fun `the average score ignores nights that were too short to score`() {
        // 24 min of snoring in 8 h is a score of 17; 72 min is 50; a 20-minute recording has no score.
        val good = night(1, 24)
        val bad = night(2, 72)
        val shortNight = sunday.minusDays(3).atTime(23, 0).atZone(zone).toInstant().toEpochMilli() to
            (sunday.minusDays(3).atTime(23, 20).atZone(zone).toInstant().toEpochMilli())
        val short = NightEntity("short", shortNight.first, shortNight.second)
        val digest = build(good, bad, short to emptyList())
        assertEquals(3, digest.nightCount)
        assertEquals(34, digest.averageScore)
    }

    @Test
    fun `a week with only unscoreable nights has no average`() {
        val short = sunday.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val digest = build(NightEntity("s", short, short + 10 * 60_000) to emptyList())
        assertEquals(1, digest.nightCount)
        assertNull(digest.averageScore)
    }

    @Test
    fun `worse nights are the ones that scored higher than the night before`() {
        val digest = build(night(3, 10), night(2, 72), night(1, 24))
        assertEquals(1, digest.worseThanPrevCount)
    }

    @Test
    fun `nights older than the lookback window are ignored`() {
        val digest = build(night(1, 24), night(8, 72))
        assertEquals(1, digest.nightCount)
    }

    @Test
    fun `the line uses Polish plurals and includes the average and the worse nights`() {
        assertTrue(WeeklyDigestBuilder.line(build(night(1, 0))).startsWith("1 noc"))
        assertTrue(WeeklyDigestBuilder.line(build(night(1, 0), night(2, 0))).startsWith("2 noce"))
        val five = WeeklyDigestBuilder.line(build(night(1, 0), night(2, 0), night(3, 0), night(4, 0), night(5, 0)))
        assertTrue(five, five.startsWith("5 nocy"))
        val digest = build(night(2, 10), night(1, 72))
        val line = WeeklyDigestBuilder.line(digest)
        assertTrue(line, line.contains("średni wynik"))
        assertTrue(line, line.contains("noc gorsza od poprzedniej"))
    }

    @Test
    fun `a line is only produced for a digest that is not empty`() {
        assertFalse(build(night(1, 24)).isEmpty)
        assertEquals("", WeeklyDigestBuilder.line(WeeklyDigest(0, null, 0, isEmpty = true)))
    }
}