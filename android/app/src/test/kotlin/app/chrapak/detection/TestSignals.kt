package app.chrapak.detection

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic audio for tests, at the pipeline's 16 kHz. */
object TestSignals {
    const val RATE = 16_000

    /** Quiet room: low random noise. */
    fun silence(seconds: Double, seed: Int = 1): ShortArray {
        val random = Random(seed)
        return ShortArray((seconds * RATE).toInt()) { random.nextInt(-20, 21).toShort() }
    }

    /** A low-frequency tone, the stand-in for a snore: strong energy below 600 Hz. */
    fun snore(seconds: Double, amplitude: Int = 3_000, hz: Double = 150.0): ShortArray =
        ShortArray((seconds * RATE).toInt()) { i -> (amplitude * sin(2 * PI * hz * i / RATE)).toInt().toShort() }

    /** Broadband noise: loud but not snore-like, since its energy is spread across the spectrum. */
    fun hiss(seconds: Double, amplitude: Int = 3_000, seed: Int = 2): ShortArray {
        val random = Random(seed)
        return ShortArray((seconds * RATE).toInt()) { random.nextInt(-amplitude, amplitude + 1).toShort() }
    }

    fun concat(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var pos = 0
        for (part in parts) {
            part.copyInto(out, pos)
            pos += part.size
        }
        return out
    }
}
