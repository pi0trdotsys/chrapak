package app.chrapak.detection

import kotlin.math.abs

/**
 * What the spacing of activity bursts inside one episode says about its rhythm. Snoring follows
 * breathing: bursts about 2-8 s apart, fairly evenly. A cough, a door or a bed frame makes bursts
 * at random intervals, so the regular spacing is evidence the gate cannot get on its own.
 *
 * Pure and cheap: called once per episode, never per frame.
 */
data class BurstRhythm(
    /** How many separate bursts of activity were found. */
    val bursts: Int,
    /** Median time between the starts of consecutive bursts; 0 when there are fewer than two. */
    val medianIntervalMs: Long,
    /** 1 minus the relative median absolute deviation: 1 is a metronome, 0 is chaos. */
    val regularity: Float,
    /** 0..1 confidence that this is a breathing rhythm; 0 when there are too few or too slow bursts. */
    val score: Float,
) {
    companion object {
        val NONE = BurstRhythm(0, 0L, 0f, 0f)

        /** A breath cycle is 2-8 s. Anything outside is not snoring, however regular. */
        const val MIN_INTERVAL_MS = 2_000L
        const val MAX_INTERVAL_MS = 8_000L
        /** Fewer bursts than this give no usable median. */
        const val MIN_BURSTS = 3
        /** Below this regularity the spacing is no better than chance. */
        const val MIN_REGULARITY = 0.4f

        /**
         * Builds the rhythm from the times at which bursts started. Consecutive intervals are what
         * matter: the gaps themselves are not used, so a long preamble never skews the result.
         */
        fun of(burstStarts: List<Long>): BurstRhythm {
            val bursts = burstStarts.size
            if (bursts < MIN_BURSTS) return BurstRhythm(bursts, 0L, 0f, 0f)
            val intervals = burstStarts.zipWithNext { a, b -> b - a }
            val median = medianOf(intervals)
            if (median < MIN_INTERVAL_MS || median > MAX_INTERVAL_MS) return BurstRhythm(bursts, median, 0f, 0f)
            val deviations = intervals.map { abs(it - median) }
            val regularity = (1.0 - medianOf(deviations).toDouble() / median).toFloat().coerceIn(0f, 1f)
            val score = ((regularity - MIN_REGULARITY) / (1f - MIN_REGULARITY)).coerceIn(0f, 1f)
            return BurstRhythm(bursts, median, regularity, score)
        }

        private fun medianOf(values: List<Long>): Long {
            if (values.isEmpty()) return 0L
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}