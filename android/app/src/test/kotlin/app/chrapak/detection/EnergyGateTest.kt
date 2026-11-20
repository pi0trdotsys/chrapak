package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGateTest {
    private val config = GateConfig()

    /** Runs the whole signal through a fresh gate and returns the active flag of every frame. */
    private fun activity(signal: ShortArray, gateConfig: GateConfig = config): List<Boolean> {
        val gate = EnergyGate(gateConfig)
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
    fun `broadband hiss is rejected when the optional low band check is on`() {
        val strict = GateConfig(minLowBandRatio = 0.4f)
        val flags = activity(TestSignals.concat(TestSignals.silence(5.0), TestSignals.hiss(3.0)), strict)
        val activeShare = flags.drop(5 * framesPerSecond()).count { it }.toDouble() / (3 * framesPerSecond())
        assertTrue("hiss was active ${(activeShare * 100).toInt()}% of the time", activeShare < 0.05)
    }

    @Test
    fun `low band check is off by default`() {
        val flags = activity(TestSignals.concat(TestSignals.silence(5.0), TestSignals.hiss(3.0)))
        assertTrue("loud hiss should pass a gate with the check off", flags.drop(5 * framesPerSecond()).count { it } > framesPerSecond())
    }

    @Test
    fun `quiet UNPROCESSED level snoring still activates`() {
        // About -60 dBFS in band: far below the old -62 dB floor but 25 dB above an empty room.
        val flags = activity(TestSignals.concat(TestSignals.silence(5.0), TestSignals.snore(2.0, amplitude = 45), TestSignals.silence(2.0)))
        assertTrue(flags.subList(5 * framesPerSecond() + 5, 7 * framesPerSecond() - 2).count { it } > framesPerSecond())
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
