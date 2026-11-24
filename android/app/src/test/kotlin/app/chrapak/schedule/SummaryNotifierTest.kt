package app.chrapak.schedule

import app.chrapak.data.NightEntity
import app.chrapak.report.NightStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryNotifierTest {
    private fun stats(count: Int, suspect: Int = 0, tooShort: Boolean = false, nightMs: Long = 8 * 3_600_000L) = NightStats(
        nightMs = nightMs,
        snoringMs = 20 * 60_000L,
        percent = 4,
        count = count,
        peakDb = -40f,
        longest = null,
        score = 17,
        tooShort = tooShort,
        suspectCount = suspect,
    )

    @Test
    fun `a quiet night says so`() {
        val text = SummaryNotifier.text(stats(count = 0, nightMs = 3 * 3_600_000L))
        assertTrue(text, text.contains("nie wykryłem chrapania"))
    }

    @Test
    fun `the summary uses Polish plurals for episodes`() {
        assertTrue(SummaryNotifier.text(stats(count = 1)).contains("1 epizod "))
        assertTrue(SummaryNotifier.text(stats(count = 3)).contains("3 epizody "))
        assertTrue(SummaryNotifier.text(stats(count = 5)).contains("5 epizodów "))
    }

    @Test
    fun `suspect recordings are mentioned only when there are some`() {
        assertFalse(SummaryNotifier.text(stats(count = 2)).contains("do sprawdzenia"))
        val withSuspect = SummaryNotifier.text(stats(count = 2, suspect = 3))
        assertTrue(withSuspect, withSuspect.contains("3 do sprawdzenia"))
    }

    @Test
    fun `a short session with no snoring is not worth a notification`() {
        val night = NightEntity("n", 0, 10 * 60_000)
        assertFalse(SummaryNotifier.shouldPost(night, stats(count = 0, nightMs = 10 * 60_000)))
        assertTrue(SummaryNotifier.shouldPost(night, stats(count = 0, nightMs = 40 * 60_000)))
        assertTrue(SummaryNotifier.shouldPost(night, stats(count = 1, nightMs = 10 * 60_000)))
    }
}