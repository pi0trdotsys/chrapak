package app.chrapak.schedule

/**
 * The first minutes of a nap guessed from the phone's state are on probation. If the room sounds
 * like someone awake (speech, television, music, typing, footsteps), the guess was wrong and the
 * nap is called off. One loud moment is not enough, so a few windows must agree.
 */
class NapProbation(
    private val probationMs: Long = DEFAULT_PROBATION_MS,
    private val awakeThreshold: Float = DEFAULT_THRESHOLD,
    private val hitsToAbort: Int = DEFAULT_HITS,
) {
    private var hits = 0

    /** Whether the nap is still on probation [sinceStartMs] after it began. */
    fun isActive(sinceStartMs: Long): Boolean = sinceStartMs < probationMs

    /** Feed one analysed window; returns true when the nap should be called off. */
    fun onWindow(awakeScore: Float): Boolean {
        if (awakeScore >= awakeThreshold) hits++
        return hits >= hitsToAbort
    }

    companion object {
        const val DEFAULT_PROBATION_MS = 5 * 60_000L
        const val DEFAULT_THRESHOLD = 0.4f
        const val DEFAULT_HITS = 2
        const val CHECK_EVERY_MS = 15_000L
    }
}
