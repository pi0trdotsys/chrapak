package app.chrapak.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NapDetectorTest {
    private val min = 60_000L
    private val afternoon = 14 * 60
    private val lateEvening = 22 * 60

    private fun detector() = NapDetector(quietMs = 15 * min)

    private fun NapDetector.shouldStart(nowMs: Long, night: Boolean = false, capturing: Boolean = false) =
        shouldStartNap(nowMs, night, capturing)

    // ---- the quiet rule ----

    @Test
    fun `no nap while the screen is on`() {
        assertFalse(detector().shouldStart(nowMs = 100 * min))
        assertNull(detector().quietUntilMs())
    }

    @Test
    fun `a candidate once the screen has been off and the phone still for the quiet time`() {
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
    fun `never while the night window or a recording is active`() {
        val d = detector()
        d.onScreenOff(0)
        assertFalse(d.shouldStart(nowMs = 30 * min, night = true))
        assertFalse(d.shouldStart(nowMs = 30 * min, capturing = true))
    }

    @Test
    fun `quiet-for time is measured from the last motion`() {
        val d = detector()
        d.onScreenOff(0)
        d.onMotion(10 * min)
        assertEquals(10 * min, d.quietForMs(nowMs = 20 * min))
    }

    // ---- any hour outside the night, with more evidence at odd hours ----

    @Test
    fun `a nap can be a candidate at any hour outside the night`() {
        val d = detector()
        d.onScreenOff(0)
        // The quiet rule no longer looks at the clock; only the night window and a running recording block it.
        assertTrue(d.shouldStart(nowMs = 30 * min))
    }

    @Test
    fun `odd hours ask for one more sign`() {
        val d = detector()
        assertEquals(2, d.requiredSigns(afternoon))
        assertEquals(3, d.requiredSigns(lateEvening))
        assertEquals(3, d.requiredSigns(8 * 60))
        assertEquals(2, d.requiredSigns(11 * 60))
        assertEquals(3, d.requiredSigns(20 * 60))
        val twoSigns = NapSignals(flat = true, charging = true)
        assertTrue(d.assess(twoSigns, quietForMs = 15 * min, minuteOfDay = afternoon).allow)
        assertFalse(d.assess(twoSigns, quietForMs = 15 * min, minuteOfDay = lateEvening).allow)
        assertTrue(d.assess(NapSignals(flat = true, charging = true, lux = 5f), 15 * min, lateEvening).allow)
    }

    // ---- the sensor verdict ----

    @Test
    fun `a covered phone is never a nap`() {
        val verdict = detector().assess(NapSignals(covered = true, flat = true, lux = 0f, charging = true), 60 * min, afternoon)
        assertFalse(verdict.allow)
    }

    @Test
    fun `a held phone is never a nap`() {
        val verdict = detector().assess(NapSignals(held = true, flat = true, lux = 3f, charging = true), 60 * min, afternoon)
        assertFalse(verdict.allow)
    }

    @Test
    fun `needs two signs and ignores unknown sensors`() {
        val d = detector()
        assertFalse(d.assess(NapSignals(), 15 * min, afternoon).allow)
        assertFalse(d.assess(NapSignals(flat = true), 15 * min, afternoon).allow)
        assertTrue(d.assess(NapSignals(flat = true, lux = 5f), 15 * min, afternoon).allow)
        assertTrue(d.assess(NapSignals(charging = true, lux = 10f), 15 * min, afternoon).allow)
    }

    @Test
    fun `darkness does not count for a phone lying face down`() {
        // Face down the light sensor reads zero in any room, so "dark" proves nothing.
        val faceDown = NapSignals(flat = true, faceDown = true, lux = 0f)
        assertFalse(detector().assess(faceDown, 15 * min, afternoon).allow) // only "flat"
        val faceUp = NapSignals(flat = true, faceDown = false, lux = 0f)
        assertTrue(detector().assess(faceUp, 15 * min, afternoon).allow) // flat and dark
    }

    @Test
    fun `a phone on a bright desk is not enough`() {
        assertFalse(detector().assess(NapSignals(flat = true, lux = 400f, charging = false), 15 * min, afternoon).allow)
    }

    @Test
    fun `long quiet counts as a sign`() {
        assertTrue(detector().assess(NapSignals(flat = true), 30 * min, afternoon).allow)
    }
}
