package app.chrapak.detection

data class SnoreSegment(val startMs: Long, val endMs: Long, val peakDb: Float, val avgDb: Float) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Turns per-frame "active" flags into snoring episodes. Snores come in breath cycles with short
 * pauses, so activity separated by less than [mergeGapMs] belongs to one episode. Episodes
 * shorter than [minEventMs] are dropped.
 */
class SnoreSegmenter(
    private val mergeGapMs: Long = 2_000,
    private val minEventMs: Long = 3_000,
) {
    private var startMs = -1L
    private var lastActiveEndMs = 0L
    private var peakDb = Float.NEGATIVE_INFINITY
    private var sumDb = 0.0
    private var activeFrames = 0

    val isOpen: Boolean get() = startMs >= 0

    /** Returns a finished episode when this frame closes one, otherwise null. */
    fun onFrame(frameStartMs: Long, frameMs: Int, active: Boolean, levelDb: Float): SnoreSegment? {
        if (active) {
            if (!isOpen) startMs = frameStartMs
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
        val segment = SnoreSegment(startMs, lastActiveEndMs, peakDb, (sumDb / activeFrames).toFloat())
        startMs = -1L
        peakDb = Float.NEGATIVE_INFINITY
        sumDb = 0.0
        activeFrames = 0
        return if (segment.durationMs >= minEventMs) segment else null
    }
}
