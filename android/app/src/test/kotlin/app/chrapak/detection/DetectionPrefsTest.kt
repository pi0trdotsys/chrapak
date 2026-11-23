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
        // A very quiet room (-80 dB) lowers LOW's -68 dB threshold to 6 dB above the silence.
        assertEquals(-74f, configFor(Sensitivity.LOW, -80f).gate.absoluteMinDb, 0.01f)
        // A loud room (-65 dB) would mean -59 dB: higher than the preset, so the preset stays.
        assertEquals(-68f, configFor(Sensitivity.LOW, -65f).gate.absoluteMinDb, 0.01f)
        // HIGH's -78 dB is already below -75 + 6 = -69, so it stays too.
        assertEquals(-78f, configFor(Sensitivity.HIGH, -75f).gate.absoluteMinDb, 0.01f)
        // A dead-silent room (-95 dB) lowers it only to the -80 dB limit.
        assertEquals(-80f, configFor(Sensitivity.MEDIUM, -95f).gate.absoluteMinDb, 0.01f)
        for (s in Sensitivity.entries) for (floor in listOf(-95f, -80f, -70f, -50f)) {
            assert(configFor(s, floor).gate.absoluteMinDb <= s.absoluteMinDb)
        }
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