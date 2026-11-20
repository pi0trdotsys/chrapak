package app.chrapak.analysis

import app.chrapak.audio.WavReader
import app.chrapak.audio.WavWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAnalyzerTest {
    private val rate = 16_000

    /** [count] snores of [snoreMs] every [periodMs] over a quiet room. */
    private fun snoring(count: Int, periodMs: Int, snoreMs: Int = 1_200, hz: Double = 150.0): ShortArray {
        val random = Random(3)
        val out = ShortArray(count * periodMs * rate / 1000) { random.nextInt(-15, 16).toShort() }
        for (n in 0 until count) {
            val start = n * periodMs * rate / 1000
            for (i in 0 until snoreMs * rate / 1000) {
                out[start + i] = (3_500 * sin(2 * PI * hz * i / rate)).toInt().toShort()
            }
        }
        return out
    }

    @Test
    fun `finds the snores and the breathing rhythm`() {
        val analysis = ClipAnalyzer.analyze(snoring(count = 10, periodMs = 4_000), rate)
        assertEquals(10, analysis.bursts.size)
        assertEquals(15.0, analysis.burstsPerMinute!!, 1.0) // one snore every 4 s
        assertEquals(2_800.0, analysis.longestPauseMs!!.toDouble(), 300.0)
    }

    @Test
    fun `a longer pause between snores is reported as the longest`() {
        val first = snoring(count = 5, periodMs = 4_000)
        val second = snoring(count = 5, periodMs = 4_000)
        // A quiet room, not digital silence: no microphone produces exact zeros.
        val noise = Random(9)
        val gap = ShortArray(12 * rate) { noise.nextInt(-15, 16).toShort() }
        val analysis = ClipAnalyzer.analyze(first + gap + second, rate)
        assertTrue("longest pause ${analysis.longestPauseMs}", analysis.longestPauseMs!! > 12_000)
    }

    @Test
    fun `a quiet clip has no snores and no rhythm`() {
        val analysis = ClipAnalyzer.analyze(ShortArray(10 * rate) { Random(5).nextInt(-10, 11).toShort() }, rate)
        assertTrue(analysis.bursts.isEmpty())
        assertNull(analysis.burstsPerMinute)
        assertNull(analysis.longestPauseMs)
    }

    @Test
    fun `fewer than three bursts give no rate`() {
        val analysis = ClipAnalyzer.analyze(snoring(count = 2, periodMs = 4_000), rate)
        assertNull(analysis.burstsPerMinute)
        assertNotNull(analysis.longestPauseMs)
    }

    @Test
    fun `energy lands in the band of the tone`() {
        val low = ClipAnalyzer.analyze(snoring(5, 3_000, hz = 200.0), rate).bandShare
        assertTrue("low tone: $low", low[0].share > 0.8f)
        val high = ClipAnalyzer.analyze(snoring(5, 3_000, hz = 1_500.0), rate).bandShare
        assertTrue("high tone: $high", high[3].share > 0.8f)
        assertEquals(1f, low.sumOf { it.share.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun `waveform is normalised and a wav round trips`() {
        val samples = snoring(3, 3_000)
        val analysis = ClipAnalyzer.analyze(samples, rate)
        assertEquals(1f, analysis.waveform.max(), 0.0001f)
        val file = File.createTempFile("clip", ".wav")
        try {
            WavWriter.write(file, samples, rate)
            val wav = WavReader.read(file)
            assertEquals(rate, wav.sampleRate)
            assertEquals(samples.size, wav.samples.size)
            assertEquals(samples[1000], wav.samples[1000])
        } finally {
            file.delete()
        }
    }
}
