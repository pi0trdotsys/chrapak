package app.chrapak.detection

data class SnoreSegment(
    val startMs: Long,
    val endMs: Long,
    val peakDb: Float,
    val avgDb: Float,
    /** How regular the bursts inside the episode were; [BurstRhythm.NONE] when there were too few. */
    val rhythm: BurstRhythm = BurstRhythm.NONE,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Turns per-frame "active" flags into snoring episodes. Snores come in breath cycles with short
 * pauses, so activity separated by less than [mergeGapMs] belongs to one episode. Episodes
 * shorter than [minEventMs] are dropped, and so are sparse ones: a handful of noise frames spread
 * over a long stretch must not pass as snoring, so the frames actually active must add up to at
 * least [minActiveMs].
 */
class SnoreSegmenter(
    private val mergeGapMs: Long = 2_000,
    private val minEventMs: Long = 3_000,
    private val minActiveMs: Long = 0,
    /** A pause longer than this inside an episode starts a new burst, used for the rhythm. */
    private val burstGapMs: Long = 400,
) {
    private var frameMs = 0
    private var startMs = -1L
    private var lastActiveEndMs = 0L
    private var peakDb = Float.NEGATIVE_INFINITY
    private var sumDb = 0.0
    private var activeFrames = 0
    private val burstStarts = mutableListOf<Long>()

    val isOpen: Boolean get() = startMs >= 0

    /** Returns a finished episode when this frame closes one, otherwise null. */
    fun onFrame(frameStartMs: Long, frameMs: Int, active: Boolean, levelDb: Float): SnoreSegment? {
        this.frameMs = frameMs
        if (active) {
            if (!isOpen) {
                startMs = frameStartMs
                burstStarts.clear()
                burstStarts += frameStartMs
            } else if (frameStartMs - lastActiveEndMs > burstGapMs) {
                // The pause was long enough to be a separate sound, not a wobble inside one snore.
                burstStarts += frameStartMs
            }
            lastActiveEndMs = frameStartMs + frameMs
            if (levelDb > peakDb) peakDb = levelDb
            sumDb += levelDb
            activeFrames++
            return null
        }
        if (isOpen && frameStartMs - lastActiveEndMs >= mergeGapMs) return close()
        return null
    }

    /** Closes the open episode, if any, e.g. when listening stops. */
    fun flush(): SnoreSegment? = if (isOpen) close() else null

    private fun close(): SnoreSegment? {
        val rhythm = BurstRhythm.of(burstStarts.toList())
        val segment = SnoreSegment(startMs, lastActiveEndMs, peakDb, (sumDb / activeFrames).toFloat(), rhythm)
        val activeMs = activeFrames.toLong() * frameMs
        startMs = -1L
        peakDb = Float.NEGATIVE_INFINITY
        sumDb = 0.0
        activeFrames = 0
        burstStarts.clear()
        return if (segment.durationMs >= minEventMs && activeMs >= minActiveMs) segment else null
    }
}
