package app.chrapak.schedule

import app.chrapak.detection.NapEvidence

enum class NapStage {
    /** The phone's state suggests a nap, but nothing in the room has confirmed it yet. */
    CHECKING,

    /** Someone is asleep nearby: snoring, or steady breathing in a quiet room. */
    CONFIRMED,

    /** Wrong guess, or no sign of a sleeper in time. */
    ABORTED,
}

/**
 * Turns the room's sounds into a decision about a nap that was only a guess. Awake sounds (speech,
 * television, typing, footsteps) call it off; snoring or steady breathing confirm it; silence alone
 * decides nothing, and after [maxCheckMs] without any sign the guess is dropped. The live status in
 * the notification waits for [NapStage.CONFIRMED], so a wrong guess never shows as a nap.
 */
class NapJudge(
    private val maxCheckMs: Long = DEFAULT_MAX_CHECK_MS,
    private val awakeThreshold: Float = DEFAULT_AWAKE,
    private val hitsToAbort: Int = DEFAULT_HITS_TO_ABORT,
    private val snoringThreshold: Float = DEFAULT_SNORING,
    private val breathingThreshold: Float = DEFAULT_BREATHING,
    private val breathingHitsToConfirm: Int = DEFAULT_BREATHING_HITS,
) {
    private var awakeHits = 0
    private var breathingHits = 0

    var stage = NapStage.CHECKING
        private set

    /** Feed one analysed window taken [sinceStartMs] after the nap candidate began. */
    fun onWindow(evidence: NapEvidence, sinceStartMs: Long): NapStage {
        if (stage != NapStage.CHECKING) return stage
        val awake = evidence.awake >= awakeThreshold
        if (awake) awakeHits++
        if (awakeHits >= hitsToAbort) return finish(NapStage.ABORTED)
        // A window with both awake sounds and snoring is not a sleeper in a quiet room, unless the snoring clearly wins.
        val sleeperLoud = evidence.snoring >= snoringThreshold && evidence.snoring > evidence.awake
        val sleeperBreathing = evidence.breathing >= breathingThreshold && !awake
        if (sleeperLoud) return finish(NapStage.CONFIRMED)
        if (sleeperBreathing) breathingHits++
        if (breathingHits >= breathingHitsToConfirm) return finish(NapStage.CONFIRMED)
        if (sinceStartMs >= maxCheckMs) return finish(NapStage.ABORTED)
        return stage
    }

    private fun finish(result: NapStage): NapStage {
        stage = result
        return result
    }

    companion object {
        const val DEFAULT_MAX_CHECK_MS = 20 * 60_000L
        const val DEFAULT_AWAKE = 0.4f
        const val DEFAULT_HITS_TO_ABORT = 2
        const val DEFAULT_SNORING = 0.3f
        const val DEFAULT_BREATHING = 0.25f
        const val DEFAULT_BREATHING_HITS = 3
        const val CHECK_EVERY_MS = 15_000L
    }
}
