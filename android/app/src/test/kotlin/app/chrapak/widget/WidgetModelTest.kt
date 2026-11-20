package app.chrapak.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetModelTest {
    private val min = 60_000L

    private fun content(
        checking: Boolean = false,
        listening: Boolean = false,
        running: Boolean = false,
        wanted: Boolean = false,
        since: Long? = null,
        events: Int = 0,
        now: Long = 0,
        schedule: Boolean = true,
        start: Int = 23 * 60,
    ) = WidgetModel.content(listening, checking, running, wanted, since, events, now, schedule, start)

    @Test
    fun `listening shows elapsed time and episodes`() {
        val c = content(listening = true, since = 0, events = 3, now = 83 * min)
        assertEquals(WidgetState.LISTENING, c.state)
        assertEquals("Nasłuch", c.title)
        assertEquals("1 h 23 min · 3 epizody", c.subtitle)
        assertEquals("1:23", c.short)
    }

    @Test
    fun `a quiet start says it is quiet`() {
        assertEquals("7 min · cicho", content(listening = true, since = 0, now = 7 * min).subtitle)
    }

    @Test
    fun `polish plural forms of episodes`() {
        fun sub(n: Int) = content(listening = true, since = 0, now = min, events = n).subtitle
        assertEquals("1 min · 1 epizod", sub(1))
        assertEquals("1 min · 2 epizody", sub(2))
        assertEquals("1 min · 5 epizodów", sub(5))
        assertEquals("1 min · 12 epizodów", sub(12))
        assertEquals("1 min · 22 epizody", sub(22))
    }

    @Test
    fun `standby shows when listening will start`() {
        val c = content(running = true, wanted = true)
        assertEquals(WidgetState.STANDBY, c.state)
        assertEquals("Start o 23:00", c.subtitle)
        assertEquals("Start ręczny", content(running = true, wanted = true, schedule = false).subtitle)
    }

    @Test
    fun `standby that was switched on but whose service is gone asks to open the app`() {
        val c = content(running = false, wanted = true)
        assertEquals(WidgetState.NEEDS_OPEN, c.state)
        assertEquals("Otwórz", c.short)
    }

    @Test
    fun `off when nothing is on`() {
        assertEquals(WidgetState.OFF, content().state)
    }

    @Test
    fun `listening wins over everything else`() {
        assertEquals(WidgetState.LISTENING, content(listening = true, running = true, wanted = true).state)
    }

    @Test
    fun `time labels never go negative`() {
        assertEquals("0 min", WidgetModel.minutesLabel(-5 * min))
        assertEquals("0:00", WidgetModel.shortElapsed(-1))
    }

    @Test
    fun `a nap candidate is not shown as a nap`() {
        val c = content(listening = true, checking = true, since = 0, now = 2 * min)
        assertEquals("Sprawdzam", c.title)
        assertEquals("czy to drzemka", c.subtitle)
        assertEquals("Sprawdzam", c.short)
    }
}
