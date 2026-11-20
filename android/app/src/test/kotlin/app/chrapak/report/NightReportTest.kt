package app.chrapak.report

import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.SleepStageType
import app.chrapak.health.StageSegment
import app.chrapak.health.VitalPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightReportTest {
    private val min = 60_000L
    private val night = NightEntity("n", sleepStartMs = 0, sleepEndMs = 100 * min)

    private fun event(startMin: Long, durMin: Long, peak: Float = -50f, false_: Boolean = false) =
        SnoreEventEntity("e$startMin", "n", startMin * min, durMin * min, peak, peak - 10, 0.7f, null, falsePositive = false_)

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
        assertEquals("Lekkie", NightReport.scoreLabel(39))
        assertEquals("Umiarkowane", NightReport.scoreLabel(40))
        assertEquals("Umiarkowane", NightReport.scoreLabel(69))
        assertEquals("Silne", NightReport.scoreLabel(70))
    }

    @Test
    fun `an open night ends at its last event instead of dividing by zero`() {
        val open = NightEntity("n", sleepStartMs = 0, sleepEndMs = null)
        val stats = NightReport.stats(open, listOf(event(0, 5)))
        assertEquals(5L * min, stats.nightMs)
        assertEquals(100, stats.percent)
        val empty = NightReport.stats(open, emptyList())
        assertTrue(empty.nightMs >= 1)
        assertNull(empty.peakDb)
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
