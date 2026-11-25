package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeJudgeTest {
    private fun judge(snoring: Float, awake: Float = 0f, rhythm: Float = 0f, model: Boolean = true) =
        EpisodeJudge.judge(model, snoring, awake, rhythm)

    @Test
    fun `snoring the model is sure of is kept with its score`() {
        val v = judge(0.9f)
        assertTrue(v.keep)
        assertEquals(0.9f, v.confidence, 0f)
    }

    @Test
    fun `nothing that sounds like snoring is dropped`() {
        assertFalse(judge(0.0f).keep)
        assertFalse(judge(0.09f).keep)
        // A regular sound with no snoring in it (a dripping tap) is not saved by its rhythm.
        assertFalse(judge(0.02f, rhythm = 1f).keep)
    }

    @Test
    fun `an awake room rules out weak snoring but not clear snoring`() {
        assertFalse(judge(0.2f, awake = 0.7f).keep)
        assertTrue(judge(0.6f, awake = 0.7f).keep)
        // Awake sounds below the bar do not veto anything.
        assertTrue(judge(0.2f, awake = 0.3f).keep)
    }

    @Test
    fun `a breathing rhythm turns weak snoring into counted snoring`() {
        val v = judge(0.15f, rhythm = 0.8f)
        assertTrue(v.keep)
        assertEquals(DetectionConfig.MIN_CONFIDENCE, v.confidence, 0f)
    }

    @Test
    fun `weak snoring without rhythm stays uncertain`() {
        val v = judge(0.15f)
        assertTrue(v.keep)
        assertTrue(v.confidence < DetectionConfig.MIN_CONFIDENCE)
    }

    @Test
    fun `without a model the gate decides alone`() {
        val v = judge(0f, model = false)
        assertTrue(v.keep)
        assertEquals(GATE_ONLY_CONFIDENCE, v.confidence, 0f)
    }

    @Test
    fun `uncertain budget keeps the most likely clips`() {
        val budget = UncertainBudget(cap = 2)
        assertNull(budget.offer("a", 0.2f))
        assertNull(budget.offer("b", 0.12f))
        assertEquals("b", budget.offer("c", 0.25f)) // c is better than b, so b goes
        assertEquals("d", budget.offer("d", 0.11f)) // d is the weakest itself: not kept
        assertEquals("a", budget.offer("e", 0.28f))
    }
}
