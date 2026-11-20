package app.chrapak.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NapDetectorTest {
    private val min = 60_000L
    private val afternoon = 14 * 60

    private fun detector() = NapDetector(quietMs = 15 * min, fromMinute = 11 * 60, toMinute = 20 * 60)

    private fun NapDetector.shouldStart(nowMs: Long, minute: Int = afternoon, night: Boolean = false, capturing: Boolean = false) =
        shouldStartNap(nowMs, minute, night, capturing)

    @Test
    fun `no nap while the screen is on`() {
        assertFalse(detector().shouldStart(nowMs = 100 * min))
        assertNull(detector().quietUntilMs())
    }

    @Test
    fun `starts once the screen has been off and the phone still for the quiet time`() {
        val d = detector()
        d.onScreenOff(nowMs = 0)
        assertFalse(d.shouldStart(nowMs = 14 * min))
        assertTrue(d.shouldStart(nowMs = 15 * min))
        assertTrue(d.shouldStart(nowMs = 40 * min))
    }

    @Test
    fun `motion restarts the quiet period`() {
        val d = detector()
        d.onScreenOff(0)
        d.onMotion(10 * min) // picked up at minute 10: quiet again until minute 25
        assertFalse(d.shouldStart(nowMs = 20 * min))
        assertEquals(25 * min, d.quietUntilMs())
        assertTrue(d.shouldStart(nowMs = 25 * min))
    }

    @Test
    fun `motion before the screen went off does not count`() {
        val d = detector()
        d.onMotion(5 * min)
        d.onScreenOff(10 * min)
        assertEquals(25 * min, d.quietUntilMs())
    }

    @Test
    fun `turning the screen on cancels the nap`() {
        val d = detector()
        d.onScreenOff(0)
        d.onScreenOn()
        assertFalse(d.shouldStart(nowMs = 60 * min))
        assertFalse(d.screenIsOff)
    }

    @Test
    fun `only during the nap hours of the day`() {
        val d = detector()
        d.onScreenOff(0)
        assertFalse(d.shouldStart(nowMs = 30 * min, minute = 10 * 60 + 59))
        assertTrue(d.shouldStart(nowMs = 30 * min, minute = 11 * 60))
        assertTrue(d.shouldStart(nowMs = 30 * min, minute = 19 * 60 + 59))
        assertFalse(d.shouldStart(nowMs = 30 * min, minute = 20 * 60))
    }

    @Test
    fun `never while the night window or a recording is active`() {
        val d = detector()
        d.onScreenOff(0)
        assertFalse(d.shouldStart(nowMs = 30 * min, night = true))
        assertFalse(d.shouldStart(nowMs = 30 * min, capturing = true))
    }

    // ---- the sensor verdict that reduces false starts ----

    @Test
    fun `a covered phone is never a nap`() {
        val verdict = detector().assess(NapSignals(covered = true, flat = true, lux = 0f, charging = true), quietForMs = 60 * min)
        assertFalse(verdict.allow)
    }

    @Test
    fun `needs two signs and ignores unknown sensors`() {
        val d = detector()
        assertFalse(d.assess(NapSignals(), quietForMs = 15 * min).allow)
        assertFalse(d.assess(NapSignals(flat = true), quietForMs = 15 * min).allow)
        assertTrue(d.assess(NapSignals(flat = true, lux = 5f), quietForMs = 15 * min).allow)
        assertTrue(d.assess(NapSignals(charging = true, lux = 10f), quietForMs = 15 * min).allow)
    }

    @Test
    fun `a phone on a bright desk is not enough`() {
        // Flat but bright and unplugged, only just quiet long enough: one sign.
        assertFalse(detector().assess(NapSignals(flat = true, lux = 400f, charging = false), quietForMs = 15 * min).allow)
    }

    @Test
    fun `long quiet counts as a sign`() {
        assertTrue(detector().assess(NapSignals(flat = true), quietForMs = 30 * min).allow)
    }

    @Test
    fun `quiet-for time is measured from the last motion`() {
        val d = detector()
        d.onScreenOff(0)
        d.onMotion(10 * min)
        assertEquals(10 * min, d.quietForMs(nowMs = 20 * min))
    }
}
