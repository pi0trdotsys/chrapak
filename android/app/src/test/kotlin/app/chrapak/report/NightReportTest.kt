package app.chrapak.report

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.SleepStageType
import app.chrapak.health.StageSegment
import app.chrapak.health.VitalPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightReportTest {
    private val min = 60_000L
    private val night = NightEntity("n", sleepStartMs = 0, sleepEndMs = 100 * min)

    private fun event(startMin: Long, durMin: Long, peak: Float = -50f, false_: Boolean = false, confidence: Float = 0.7f) =
        SnoreEventEntity("e$startMin", "n", startMin * min, durMin * min, peak, peak - 10, confidence, null, falsePositive = false_)

    @Test
    fun `uncertain episodes are kept but not counted`() {
        val stats = NightReport.stats(night, listOf(event(0, 10), event(20, 30, peak = -30f, confidence = 0.1f)))
        assertEquals(1, stats.count)
        assertEquals(10, stats.snoringMinutes)
        assertEquals(-50f, stats.peakDb!!, 0f)
    }

    @Test
    fun `stats count only valid events`() {
        val stats = NightReport.stats(night, listOf(event(0, 10), event(20, 5, peak = -40f), event(50, 30, false_ = true)))
        assertEquals(2, stats.count)
        assertEquals(15, stats.snoringMinutes)
        assertEquals(15, stats.percent)
        assertEquals(-40f, stats.peakDb!!, 0f)
        assertEquals(10L * min, stats.longest!!.durationMs)
    }

    @Test
    fun `score is the share of the night against the full score share`() {
        assertEquals(50, NightReport.stats(night, listOf(event(0, 15))).score)
        assertEquals(100, NightReport.stats(night, listOf(event(0, 60))).score) // capped
        assertEquals(0, NightReport.stats(night, emptyList()).score)
    }

    @Test
    fun `labels follow the prototype thresholds`() {
        assertEquals("Lekkie", NightReport.scoreLabel(39, tooShort = false))
        assertEquals("Umiarkowane", NightReport.scoreLabel(40, tooShort = false))
        assertEquals("Umiarkowane", NightReport.scoreLabel(69, tooShort = false))
        assertEquals("Silne", NightReport.scoreLabel(70, tooShort = false))
    }

    @Test
    fun `a night in progress runs until now, and its score waits for 30 minutes`() {
        val open = NightEntity("n", sleepStartMs = 0, sleepEndMs = null)
        val early = NightReport.stats(open, listOf(event(0, 5)), nowMs = 10 * min)
        assertEquals(10L * min, early.nightMs)
        assertEquals(50, early.percent)
        assertTrue(early.tooShort)
        assertEquals(0, early.score)
        val later = NightReport.stats(open, listOf(event(0, 5)), nowMs = 50 * min)
        assertFalse(later.tooShort)
        assertEquals(33, later.score)
        // An event reported after the clock moved on still counts in full, and nothing divides by zero.
        assertEquals(5L * min, NightReport.stats(open, listOf(event(0, 5)), nowMs = 0).nightMs)
        val empty = NightReport.stats(open, emptyList(), nowMs = 0)
        assertTrue(empty.nightMs >= 1)
        assertNull(empty.peakDb)
    }

    @Test
    fun `a night shorter than 30 minutes has score 0`() {
        val open = NightEntity("n", sleepStartMs = 0, sleepEndMs = null)
        val stats = NightReport.stats(open, listOf(event(0, 5)), nowMs = 25 * 60_000L)
        assertTrue(stats.tooShort)
        assertEquals(0, stats.score)
        assertEquals("Za krótka", NightReport.scoreLabel(stats.score, stats.tooShort))
    }

    @Test
    fun `a finished short test run gets no score either`() {
        val short = NightEntity("n", sleepStartMs = 0, sleepEndMs = 2 * min)
        val stats = NightReport.stats(short, listOf(event(0, 1)))
        assertTrue(stats.tooShort)
        assertEquals(0, stats.score)
        assertEquals(50, stats.percent)
    }

    @Test
    fun `a night 30+ minutes gets a normal score even if open`() {
        val open = NightEntity("n", sleepStartMs = 0, sleepEndMs = null)
        val stats = NightReport.stats(open, listOf(event(0, 10)), nowMs = 35 * 60_000L)
        assertFalse(stats.tooShort)
        assertTrue(stats.score > 0)
    }

    @Test
    fun `suspect episodes are counted separately from snoring and from false alarms`() {
        val stats = NightReport.stats(
            night,
            listOf(
                event(0, 10),                      // counted
                event(20, 10, confidence = 0.1f),  // saved, not sure → suspect
                event(40, 10, confidence = 0.05f), // another suspect
                event(60, 10, false_ = true),      // marked as a false alarm: neither counted nor suspect
            ),
        )
        assertEquals(1, stats.count)
        assertEquals(2, stats.suspectCount)
    }

    private val stages = listOf(
        StageSegment(0, 50 * min, SleepStageType.LIGHT),
        StageSegment(50 * min, 100 * min, SleepStageType.REM),
    )

    @Test
    fun `snoring is split by stage using overlap`() {
        // 10 min from minute 45 to 55: 5 min in LIGHT and 5 min in REM.
        val byStage = NightReport.snoringMsByStage(listOf(event(45, 10)), stages)
        assertEquals(5L * min, byStage[SleepStageType.LIGHT])
        assertEquals(5L * min, byStage[SleepStageType.REM])
    }

    @Test
    fun `REM ratio compares rates per minute of stage time`() {
        // REM: 10 min of snoring in 50 min; LIGHT: 5 min in 50 min, so twice as often in REM.
        val ratio = NightReport.remRatio(listOf(event(0, 5), event(60, 10)), stages)
        assertEquals(2.0, ratio!!, 1e-9)
    }

    @Test
    fun `REM ratio is null without a hypnogram or without snoring outside REM`() {
        assertNull(NightReport.remRatio(listOf(event(0, 5)), emptyList()))
        assertNull(NightReport.remRatio(listOf(event(60, 10)), stages))
    }

    @Test
    fun `lowest SpO2 near episodes only looks inside episode windows`() {
        val spo2 = listOf(
            VitalPoint(1 * min, 80.0, "x"), // far from any episode: ignored
            VitalPoint(21 * min, 91.0, "x"), // 1 min after the episode ends: inside the 90 s window
            VitalPoint(23 * min, 70.0, "x"), // outside the window
            VitalPoint(12 * min, 95.0, "x"),
        )
        val dip = NightReport.lowestSpo2NearEvents(listOf(event(10, 10)), spo2)
        assertNotNull(dip)
        assertEquals(91.0, dip!!.value, 0.0)
        assertEquals(70.0, NightReport.lowestSpo2(spo2)!!.value, 0.0)
        assertNull(NightReport.lowestSpo2NearEvents(emptyList(), spo2))
        assertNull(NightReport.lowestSpo2NearEvents(listOf(event(10, 10)), emptyList()))
    }
}
