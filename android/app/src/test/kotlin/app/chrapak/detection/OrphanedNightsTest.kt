package app.chrapak.detection

import app.chrapak.data.NightDao
import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventDao
import app.chrapak.data.SnoreEventEntity
import app.chrapak.data.closeOrphanedNights
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
    }

    private class FakeEvents(val rows: List<SnoreEventEntity>) : SnoreEventDao {
        override suspend fun insert(event: SnoreEventEntity) = error("not used")
        override suspend fun count(nightId: String) = rows.count { it.nightId == nightId }
        override suspend fun lastEventEndMs(nightId: String) =
            rows.filter { it.nightId == nightId }.maxOfOrNull { it.startMs + it.durationMs }
    }

    private fun event(night: String, start: Long, duration: Long) =
        SnoreEventEntity("$night-$start", night, start, duration, -50f, -60f, 0.5f, null)

    @Test
    fun `open nights end at their last event or at their start`() = runBlocking {
        val nights = FakeNights(
            mutableListOf(
                NightEntity("a", 1_000, null),
                NightEntity("b", 5_000, null),
                NightEntity("c", 9_000, 9_500), // already closed: untouched
            ),
        )
        val events = FakeEvents(listOf(event("a", 2_000, 3_000), event("a", 7_000, 500)))

        closeOrphanedNights(nights, events)

        assertEquals(7_500L, nights.rows.first { it.id == "a" }.sleepEndMs)
        assertEquals(5_000L, nights.rows.first { it.id == "b" }.sleepEndMs)
        assertEquals(9_500L, nights.rows.first { it.id == "c" }.sleepEndMs)
    }
}
