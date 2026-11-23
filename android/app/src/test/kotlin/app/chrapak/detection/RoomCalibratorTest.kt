package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RoomCalibratorTest {
    private val rate = 16_000

    private fun tone(seconds: Double, amplitude: Double, hz: Double = 300.0) =
        ShortArray((seconds * rate).toInt()) { i -> (amplitude * 32767 * sin(2 * PI * hz * i / rate)).toInt().toShort() }

    @Test
    fun `floor of a steady hum is its level`() {
        val quiet = RoomCalibrator.floorOf(tone(3.0, 0.001), (3 * rate))!!
        val loud = RoomCalibrator.floorOf(tone(3.0, 0.01), (3 * rate))!!
        // Ten times the amplitude is 20 dB more.
        assertEquals(20f, loud - quiet, 1.5f)
    }

    @Test
    fun `short loud sounds do not lift the floor`() {
        val samples = tone(5.0, 0.001)
        val burst = tone(0.5, 0.3)
        burst.copyInto(samples, rate * 2) // half a second of noise in the middle
        val withBurst = RoomCalibrator.floorOf(samples, samples.size)!!
        val plain = RoomCalibrator.floorOf(tone(5.0, 0.001), 5 * rate)!!
        assertTrue("floor moved from $plain to $withBurst", withBurst - plain < 1f)
    }

    @Test
    fun `too little audio gives no floor`() {
        assertNull(RoomCalibrator.floorOf(ShortArray(100), 100))
    }
}
