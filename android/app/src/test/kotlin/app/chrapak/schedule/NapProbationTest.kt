package app.chrapak.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapProbationTest {
    @Test
    fun `one awake sound is not enough to call off a nap`() {
        val p = NapProbation()
        assertFalse(p.onWindow(0.9f))
    }

    @Test
    fun `two windows of awake sounds call the nap off`() {
        val p = NapProbation()
        assertFalse(p.onWindow(0.5f))
        assertTrue(p.onWindow(0.6f))
    }

    @Test
    fun `quiet windows never call it off`() {
        val p = NapProbation()
        repeat(20) { assertFalse(p.onWindow(0.05f)) }
    }

    @Test
    fun `probation lasts only the first minutes`() {
        val p = NapProbation(probationMs = 5 * 60_000L)
        assertTrue(p.isActive(4 * 60_000L))
        assertFalse(p.isActive(5 * 60_000L))
    }
}
