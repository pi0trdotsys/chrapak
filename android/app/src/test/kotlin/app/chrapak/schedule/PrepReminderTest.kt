package app.chrapak.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepReminderTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun at(h: Int, m: Int, day: Int = 10) =
        LocalDateTime.of(2024, 3, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun decide(
        armed: Boolean = true,
        scheduleOn: Boolean = true,
        listening: Boolean = false,
        charging: Boolean = false,
        hasNightTonight: Boolean = false,
        reminderShownAtMs: Long? = null,
        nowMs: Long = at(21, 0),
        skipUntilMs: Long = 0L,
    ) = PrepReminderRule.decide(
        armed = armed,
        scheduleOn = scheduleOn,
        listening = listening,
        charging = charging,
        hasNightTonight = hasNightTonight,
        reminderShownAtMs = reminderShownAtMs,
        nowMs = nowMs,
        skipUntilMs = skipUntilMs,
    )

    @Test
    fun `nothing is shown when standby is off`() {
        assertFalse(decide(armed = false).shouldNotify)
        assertFalse(decide(scheduleOn = false).shouldNotify)
    }

    @Test
    fun `nothing is shown when the microphone is already in use`() {
        assertFalse(decide(listening = true).shouldNotify)
        assertFalse(decide(hasNightTonight = true).shouldNotify)
    }

    @Test
    fun `a phone on the charger needs no reminder`() {
        assertFalse(decide(charging = true).shouldNotify)
    }

    @Test
    fun `the reminder comes half an hour before the window, whatever the window`() {
        assertEquals(22 * 60 + 30, AutoListenScheduler.prepMinute(23 * 60))
        // A daytime window (12:00-08:00) gets its reminder before noon, not at a fixed evening hour.
        assertEquals(11 * 60 + 30, AutoListenScheduler.prepMinute(12 * 60))
        assertEquals(23 * 60 + 40, AutoListenScheduler.prepMinute(10))
    }

    @Test
    fun `nie dzis suppresses the reminder for the rest of the evening`() {
        assertFalse(decide(skipUntilMs = at(21, 30)).shouldNotify)
        assertTrue(decide(skipUntilMs = at(20, 30)).shouldNotify)
    }

    @Test
    fun `an already shown reminder is not repeated the same evening`() {
        assertFalse(decide(reminderShownAtMs = at(21, 0) - 3_600_000L).shouldNotify)
    }

    @Test
    fun `the ordinary evening case is a yes`() {
        val decision = decide()
        assertTrue(decision.shouldNotify)
        assertTrue(decision.reason, decision.reason.isNotBlank())
    }
}