package app.chrapak.detection

import app.chrapak.data.NightDao
import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventDao
import app.chrapak.data.SnoreEventEntity
import app.chrapak.data.closeOrphanedNights
import app.chrapak.data.finishNight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OrphanedNightsTest {
    private class FakeNights(val rows: MutableList<NightEntity>) : NightDao {
        override suspend fun insert(night: NightEntity) { rows += night }
        override suspend fun finish(id: String, endMs: Long) {
            rows.replaceAll { if (it.id == id) it.copy(sleepEndMs = endMs) else it }
        }
        override suspend fun open() = rows.filter { it.sleepEndMs == null }
        override suspend fun all() = rows.sortedByDescending { it.sleepStartMs }
        override suspend fun delete(id: String) { rows.removeAll { it.id == id } }
    }

    private class FakeEvents(val rows: List<SnoreEventEntity>) : SnoreEventDao {
        override suspend fun insert(event: SnoreEventEntity) = error("not used")
        override suspend fun count(nightId: String) = rows.count { it.nightId == nightId }
        override suspend fun lastEventEndMs(nightId: String) =
            rows.filter { it.nightId == nightId }.maxOfOrNull { it.startMs + it.durationMs }
        override suspend fun forNight(nightId: String) = rows.filter { it.nightId == nightId }
        override suspend fun setStarred(id: String, starred: Boolean) = error("not used")
        override suspend fun setFalsePositive(id: String, falsePositive: Boolean) = error("not used")
        override suspend fun delete(id: String) = error("not used")
    }

    private fun event(night: String, start: Long, duration: Long) =
        SnoreEventEntity("$night-$start", night, start, duration, -50f, -60f, 0.5f, null)

    @Test
    fun `open nights end at their last event and trivial ones are dropped`() = runBlocking {
        val nights = FakeNights(
            mutableListOf(
                NightEntity("a", 1_000, null),
                NightEntity("b", 5_000, null), // no events and no duration: a test run, dropped
                NightEntity("c", 9_000, 9_500), // already closed: untouched
            ),
        )
        // The first night has an episode ending at 7.5 s, but a night under a minute with an event is kept.
        val events = FakeEvents(listOf(event("a", 2_000, 3_000), event("a", 7_000, 500)))

        closeOrphanedNights(nights, events)

        assertEquals(7_500L, nights.rows.first { it.id == "a" }.sleepEndMs)
        assertEquals(null, nights.rows.firstOrNull { it.id == "b" })
        assertEquals(9_500L, nights.rows.first { it.id == "c" }.sleepEndMs)
    }

    @Test
    fun `a long silent night is kept`() = runBlocking {
        val night = NightEntity("quiet", 0, null)
        val nights = FakeNights(mutableListOf(night))

        finishNight(nights, FakeEvents(emptyList()), night, endMs = 8 * 3_600_000L)

        assertEquals(8 * 3_600_000L, nights.rows.single().sleepEndMs)
    }

    @Test
    fun `a short night with no events is dropped when stopped`() = runBlocking {
        val night = NightEntity("blip", 0, null)
        val nights = FakeNights(mutableListOf(night))

        finishNight(nights, FakeEvents(emptyList()), night, endMs = 20_000)

        assertEquals(0, nights.rows.size)
    }
}
