package app.chrapak.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One stretch of snoring inside a clip, found from the loudness envelope. */
data class Burst(val startMs: Long, val durationMs: Long, val peakDb: Float)

data class ClipAnalysis(
    val durationMs: Long,
    /** Peak level per slice of the clip, 0..1, for drawing the waveform. */
    val waveform: List<Float>,
    val bursts: List<Burst>,
    /** Snores per minute, from the median gap between bursts; null with fewer than three bursts. */
    val burstsPerMinute: Double?,
    /** Longest silence between two bursts; null with fewer than two. */
    val longestPauseMs: Long?,
    /** Share of the energy between 100 and 2000 Hz in each band; sums to about 1. */
    val bandShare: List<BandShare>,
)

data class BandShare(val label: String, val share: Float)

/**
 * Reads a snoring clip without any model: how loud, how rhythmic, where the energy sits. Plain
 * Kotlin over samples, so it is tested on synthetic signals. These are observations from one clip,
 * not a diagnosis; a long gap between snores is only worth mentioning to a doctor.
 */
object ClipAnalyzer {
    private const val FRAME_MS = 100
    private const val WAVEFORM_BUCKETS = 120
    private const val MIN_BURST_FRAMES = 3
    private const val MERGE_GAP_FRAMES = 4
    private const val ACTIVE_MARGIN_DB = 8f

    private val BANDS = listOf("100–300 Hz" to (100 to 300), "300–600 Hz" to (300 to 600), "600–1200 Hz" to (600 to 1200), "1,2–2 kHz" to (1200 to 2000))

    fun analyze(samples: ShortArray, sampleRate: Int): ClipAnalysis {
        val durationMs = samples.size * 1000L / sampleRate
        val envelope = envelopeDb(samples, sampleRate)
        val bursts = findBursts(envelope)
        val starts = bursts.map { it.startMs }
        val gaps = starts.zipWithNext { a, b -> b - a }.sorted()
        val perMinute = if (bursts.size >= 3 && gaps.isNotEmpty()) 60_000.0 / gaps[gaps.size / 2] else null
        val pauses = bursts.zipWithNext { a, b -> b.startMs - (a.startMs + a.durationMs) }
        return ClipAnalysis(
            durationMs = durationMs,
            waveform = waveform(samples),
            bursts = bursts,
            burstsPerMinute = perMinute,
            longestPauseMs = pauses.maxOrNull(),
            bandShare = bandShare(samples, sampleRate),
        )
    }

    /** Loudness per 100 ms frame in dBFS. */
    internal fun envelopeDb(samples: ShortArray, sampleRate: Int): FloatArray {
        val frame = sampleRate * FRAME_MS / 1000
        val frames = samples.size / frame
        return FloatArray(frames) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) {
                val x = samples[i] / 32768.0
                sum += x * x
            }
            (20.0 * log10(sqrt(sum / frame) + 1e-9)).toFloat()
        }
    }

    /** Frames clearly above the clip's own quiet level are snoring; near gaps are merged, specks dropped. */
    internal fun findBursts(envelope: FloatArray): List<Burst> {
        if (envelope.isEmpty()) return emptyList()
        val floor = envelope.sorted()[(envelope.size * 0.2).toInt().coerceIn(0, envelope.size - 1)]
        val active = BooleanArray(envelope.size) { envelope[it] > floor + ACTIVE_MARGIN_DB }
        // Close short gaps first so one snore with a dip is one burst.
        var i = 0
        while (i < active.size) {
            if (!active[i]) {
                var j = i
                while (j < active.size && !active[j]) j++
                if (i > 0 && j < active.size && j - i <= MERGE_GAP_FRAMES) for (k in i until j) active[k] = true
                i = j
            } else i++
        }
        val out = mutableListOf<Burst>()
        i = 0
        while (i < active.size) {
            if (active[i]) {
                var j = i
                var peak = Float.NEGATIVE_INFINITY
                while (j < active.size && active[j]) {
                    peak = max(peak, envelope[j])
                    j++
                }
                if (j - i >= MIN_BURST_FRAMES) out += Burst(i * FRAME_MS.toLong(), (j - i) * FRAME_MS.toLong(), peak)
                i = j
            } else i++
        }
        return out
    }

    private fun waveform(samples: ShortArray): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val per = max(1, samples.size / WAVEFORM_BUCKETS)
        val peaks = (0 until WAVEFORM_BUCKETS).map { b ->
            var peak = 0
            for (i in b * per until minOf(samples.size, (b + 1) * per)) peak = max(peak, kotlin.math.abs(samples[i].toInt()))
            peak.toFloat()
        }
        val top = peaks.max().coerceAtLeast(1f)
        return peaks.map { it / top }
    }

    /** Average power spectrum over the clip, grouped into four bands. */
    internal fun bandShare(samples: ShortArray, sampleRate: Int): List<BandShare> {
        val n = 1024
        val power = DoubleArray(n / 2)
        var windows = 0
        var start = 0
        val hann = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
        while (start + n <= samples.size && windows < 400) {
            val re = DoubleArray(n) { samples[start + it] / 32768.0 * hann[it] }
            val im = DoubleArray(n)
            fft(re, im)
            for (k in 0 until n / 2) power[k] += re[k] * re[k] + im[k] * im[k]
            windows++
            start += n * 2
        }
        val binHz = sampleRate.toDouble() / n
        val sums = BANDS.map { (_, range) ->
            (0 until n / 2).filter { it * binHz >= range.first && it * binHz < range.second }.sumOf { power[it] }
        }
        val total = sums.sum().coerceAtLeast(1e-12)
        return BANDS.mapIndexed { i, (label, _) -> BandShare(label, (sums[i] / total).toFloat()) }
    }

    /** In-place radix-2 FFT; the length must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            for (i in 0 until n step len) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
            }
            len = len shl 1
        }
    }
}
