package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGateTest {
    private val config = GateConfig()

    /** Runs the whole signal through a fresh gate and returns the active flag of every frame. */
    private fun activity(signal: ShortArray): List<Boolean> {
        val gate = EnergyGate(config)
        return (0 until signal.size / config.frameSamples).map { gate.process(signal, it * config.frameSamples).active }
    }

    private fun framesPerSecond() = 1000 / config.frameMs

    @Test
    fun `quiet room never activates`() {
        assertTrue(activity(TestSignals.silence(10.0)).none { it })
    }

    @Test
    fun `low frequency burst after silence activates`() {
        val flags = activity(TestSignals.concat(TestSignals.silence(5.0), TestSignals.snore(2.0), TestSignals.silence(3.0)))
        val inBurst = flags.subList(5 * framesPerSecond() + 3, 7 * framesPerSecond() - 1)
        assertTrue("burst frames should be active", inBurst.all { it })
        assertTrue("silence before the burst should be inactive", flags.subList(0, 5 * framesPerSecond() - 1).none { it })
    }

    @Test
    fun `broadband hiss is rejected by the low band check`() {
        val flags = activity(TestSignals.concat(TestSignals.silence(5.0), TestSignals.hiss(3.0)))
        val activeShare = flags.drop(5 * framesPerSecond()).count { it }.toDouble() / (3 * framesPerSecond())
        assertTrue("hiss was active ${(activeShare * 100).toInt()}% of the time", activeShare < 0.05)
    }

    @Test
    fun `nothing is active during warm up`() {
        val gate = EnergyGate(config)
        val loud = TestSignals.snore(1.0)
        repeat(config.warmupFrames) { assertEquals(false, gate.process(loud, it * config.frameSamples % (loud.size - config.frameSamples)).active) }
    }

    @Test
    fun `noise floor tracks a constant background`() {
        val gate = EnergyGate(config)
        val background = TestSignals.hiss(4.0, amplitude = 300)
        var lastLevel = 0f
        for (i in 0 until background.size / config.frameSamples) {
            lastLevel = gate.process(background, i * config.frameSamples).levelDb
        }
        assertEquals("floor should sit at the background level", lastLevel, gate.noiseFloorDb, 3f)
    }
}
