package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DetectionPrefsTest {
    @Test
    fun `configFor returns correct presets`() {
        val low = configFor(Sensitivity.LOW, null)
        assertEquals(10f, low.gate.marginDb, 0f)
        assertEquals(-68f, low.gate.absoluteMinDb, 0f)
        assertEquals(2000L, low.minActiveMs)
        assertEquals(4000L, low.minEventMs)

        val medium = configFor(Sensitivity.MEDIUM, null)
        assertEquals(8f, medium.gate.marginDb, 0f)
        assertEquals(-70f, medium.gate.absoluteMinDb, 0f)
        assertEquals(1500L, medium.minActiveMs)
        assertEquals(3000L, medium.minEventMs)

        val high = configFor(Sensitivity.HIGH, null)
        assertEquals(5f, high.gate.marginDb, 0f)
        assertEquals(-78f, high.gate.absoluteMinDb, 0f)
        assertEquals(1000L, high.minActiveMs)
        assertEquals(2000L, high.minEventMs)
    }

    @Test
    fun `calibrated floor never raises the threshold`() {
        // Room is quiet at -80 dB, but preset for LOW is -68 dB
        val low = configFor(Sensitivity.LOW, -80f)
        assertEquals(-80f, low.gate.absoluteMinDb, 0.1f) // calibrated floor wins

        // Room is loud at -65 dB, preset is -68 dB - should keep -68 (never raise)
        val low2 = configFor(Sensitivity.LOW, -65f)
        assertEquals(-68f, low2.gate.absoluteMinDb, 0.1f) // preset wins

        // Calibrated -75 with HIGH preset (-78) -> calibrated wins
        val high = configFor(Sensitivity.HIGH, -75f)
        assertEquals(-75f, high.gate.absoluteMinDb, 0.1f)
    }

    @Test
    fun `sensitivity entries have correct labels`() {
        assertEquals("Niska", Sensitivity.LOW.label)
        assertEquals("Średnia", Sensitivity.MEDIUM.label)
        assertEquals("Wysoka", Sensitivity.HIGH.label)
    }

    @Test
    fun `configFor returns correct values with calibratedFloor null`() {
        val cfg = configFor(Sensitivity.MEDIUM, null)
        assertEquals(-70f, cfg.gate.absoluteMinDb, 0f)
    }

    @Test
    fun `all sensitivity values produce valid config`() {
        for (s in Sensitivity.entries) {
            val cfg = configFor(s, null)
            assertNotNull(cfg)
            assertNotNull(cfg.gate)
            assertEquals(cfg.gate.sampleRate, 16_000)
        }
    }
}