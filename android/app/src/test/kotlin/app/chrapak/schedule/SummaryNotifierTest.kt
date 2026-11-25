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
        assertTrue(SummaryNotifier.text(stats(count = 1)).contains(Regex("\\b1 epizod\\b")))
        assertTrue(SummaryNotifier.text(stats(count = 3)).contains(Regex("\\b3 epizody\\b")))
        assertTrue(SummaryNotifier.text(stats(count = 5)).contains(Regex("\\b5 epizodów")))
    }

    @Test
    fun `suspect recordings are mentioned only when there are some`() {
        assertFalse(SummaryNotifier.text(stats(count = 2)).contains("do sprawdzenia"))
        val withSuspect = SummaryNotifier.text(stats(count = 2, suspect = 3))
        assertTrue(withSuspect, withSuspect.contains("3 nagrania do sprawdzenia"))
    }

    @Test
    fun `a short session with no snoring is not worth a notification`() {
        val night = NightEntity("n", 0, 10 * 60_000)
        assertFalse(SummaryNotifier.shouldPost(night, stats(count = 0, nightMs = 10 * 60_000)))
        assertTrue(SummaryNotifier.shouldPost(night, stats(count = 0, nightMs = 40 * 60_000)))
        assertTrue(SummaryNotifier.shouldPost(night, stats(count = 1, nightMs = 10 * 60_000)))
    }

    @Test
    fun `the summary compares with the night before only when both have a score`() {
        val now = stats(count = 3).copy(score = 20)
        assertTrue(SummaryNotifier.text(now, stats(count = 5).copy(score = 45)).contains("Lepiej niż poprzednio (45/100)"))
        assertTrue(SummaryNotifier.text(now, stats(count = 5).copy(score = 5)).contains("Gorzej niż poprzednio (5/100)"))
        assertTrue(SummaryNotifier.text(now, stats(count = 5).copy(score = 25)).contains("Podobnie"))
        assertFalse(SummaryNotifier.text(now, stats(count = 5, tooShort = true)).contains("poprzednio"))
        assertFalse(SummaryNotifier.text(now, null).contains("poprzednio"))
    }

    @Test
    fun `the title carries the score, a nap says so`() {
        val night = NightEntity("n", 0, 60 * 60_000)
        assertTrue(SummaryNotifier.title(night, stats(count = 2), nap = false, endMs = 60 * 60_000).endsWith("· 17/100"))
        assertTrue(SummaryNotifier.title(night, stats(count = 2, tooShort = true), nap = true, endMs = 60 * 60_000).startsWith("Drzemka"))
        assertFalse(SummaryNotifier.title(night, stats(count = 0), nap = false, endMs = 60 * 60_000).contains("/100"))
        assertTrue(SummaryNotifier.text(stats(count = 2), nap = true).contains("% drzemki"))
    }
}
