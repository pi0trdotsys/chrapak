package app.chrapak.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionQualityTest {
    @Test
    fun `the model alone can make an episode confident`() {
        assertTrue(DetectionQuality.isConfident(classifierScore = 0.7f, rhythmScore = 0f))
    }

    @Test
    fun `a low model score alone leaves it uncertain`() {
        assertFalse(DetectionQuality.isConfident(classifierScore = 0.1f, rhythmScore = 0f))
    }

    @Test
    fun `the rhythm alone can make an episode confident`() {
        assertTrue(DetectionQuality.isConfident(classifierScore = null, rhythmScore = 0.8f))
    }

    @Test
    fun `without the model and without rhythm the episode stays uncertain`() {
        assertFalse(DetectionQuality.isConfident(classifierScore = null, rhythmScore = 0.3f))
    }

    @Test
    fun `the boundary values count as confident`() {
        assertTrue(DetectionQuality.isConfident(DetectionQuality.CLASSIFIER_CONFIDENT, 0f))
        assertTrue(DetectionQuality.isConfident(null, DetectionQuality.RHYTHM_CONFIDENT))
    }

    @Test
    fun `the reason prefers the model and falls back to the rhythm`() {
        assertTrue(DetectionQuality.reason(0.8f, 0.9f).contains("model i rytm"))
        assertTrue(DetectionQuality.reason(0.8f, 0f).contains("model rozpoznał"))
        assertTrue(DetectionQuality.reason(0.1f, 0.9f).contains("rytmiczne bursty"))
        assertTrue(DetectionQuality.reason(0.1f, 0f).contains("model nie był pewny"))
        assertTrue(DetectionQuality.reason(null, 0f).contains("brak modelu"))
    }
}