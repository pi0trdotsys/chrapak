package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstRhythmTest {
    @Test
    fun `no bursts give nothing`() {
        val rhythm = BurstRhythm.of(emptyList())
        assertEquals(0, rhythm.bursts)
        assertEquals(0f, rhythm.score, 0f)
    }

    @Test
    fun `two bursts are not enough for a median worth trusting`() {
        val rhythm = BurstRhythm.of(listOf(0L, 3_000L))
        assertEquals(2, rhythm.bursts)
        assertEquals(0f, rhythm.score, 0f)
    }

    @Test
    fun `four bursts three seconds apart are a metronome`() {
        val rhythm = BurstRhythm.of(listOf(0L, 3_000L, 6_000L, 9_000L))
        assertEquals(4, rhythm.bursts)
        assertEquals(3_000L, rhythm.medianIntervalMs)
        assertEquals(1f, rhythm.regularity, 0.001f)
        assertEquals(1f, rhythm.score, 0.001f)
        assertTrue(rhythm.score >= DetectionQuality.RHYTHM_CONFIDENT)
    }

    @Test
    fun `a five second breathing rhythm scores high`() {
        val rhythm = BurstRhythm.of(listOf(0L, 5_000L, 10_000L))
        assertEquals(5_000L, rhythm.medianIntervalMs)
        assertEquals(1f, rhythm.score, 0.001f)
    }

    @Test
    fun `bursts far too close together are not breathing`() {
        val rhythm = BurstRhythm.of(listOf(0L, 500L, 1_000L))
        assertEquals(500L, rhythm.medianIntervalMs)
        assertEquals(0f, rhythm.score, 0f)
    }

    @Test
    fun `bursts far too far apart are not breathing`() {
        val rhythm = BurstRhythm.of(listOf(0L, 20_000L, 40_000L))
        assertEquals(20_000L, rhythm.medianIntervalMs)
        assertEquals(0f, rhythm.score, 0f)
    }

    @Test
    fun `irregular cough-like spacing is not snoring`() {
        // Intervals 1 s, 5 s, 3 s: the median is 3 s but the spread eats the regularity.
        val rhythm = BurstRhythm.of(listOf(0L, 1_000L, 6_000L, 9_000L))
        assertEquals(3_000L, rhythm.medianIntervalMs)
        assertTrue("regularity ${rhythm.regularity}", rhythm.regularity < BurstRhythm.MIN_REGULARITY)
        assertEquals(0f, rhythm.score, 0f)
    }

    @Test
    fun `a small jitter keeps a middling score`() {
        // Intervals 2 s and 4 s around a 3 s median: regularity 2/3.
        val rhythm = BurstRhythm.of(listOf(0L, 2_000L, 6_000L))
        assertEquals(3_000L, rhythm.medianIntervalMs)
        assertEquals(0.667f, rhythm.regularity, 0.01f)
        assertEquals(0.444f, rhythm.score, 0.02f)
    }

    @Test
    fun `the median ignores one odd interval`() {
        val rhythm = BurstRhythm.of(listOf(0L, 3_000L, 6_000L, 9_000L, 21_000L))
        // Intervals 3, 3, 3, 12: median 3 s, deviations 0,0,0,9 s → median 0 → perfectly regular.
        assertEquals(3_000L, rhythm.medianIntervalMs)
        assertEquals(1f, rhythm.regularity, 0.001f)
    }

    @Test
    fun `the score never leaves the zero to one range`() {
        for (starts in listOf(
            listOf(0L, 2_000L, 4_000L, 6_000L),
            listOf(0L, 8_000L, 16_000L),
            listOf(0L, 1_000L, 3_000L, 9_000L),
        )) {
            val score = BurstRhythm.of(starts).score
            assertTrue("score $score", score in 0f..1f)
        }
    }
}