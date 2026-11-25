package app.chrapak.detection

/**
 * How much to trust one episode, given what the gate knew on its own. The classifier and the
 * rhythm are independent evidence: either one being confident is enough, and neither can delete
 * an episode (its audio is always kept).
 */
object DetectionQuality {
    /** A classifier score at or above this is "probably snoring" (YAMNet is rarely above 0.7 on snoring). */
    const val CLASSIFIER_CONFIDENT = 0.5f

    /** A rhythm score at or above this is a regular breathing pattern, even without the model. */
    const val RHYTHM_CONFIDENT = 0.6f

    fun isConfident(classifierScore: Float?, rhythmScore: Float): Boolean =
        (classifierScore ?: 0f) >= CLASSIFIER_CONFIDENT || rhythmScore >= RHYTHM_CONFIDENT

    /**
     * Why an episode is or is not confident, for the UI: what the user should know before listening.
     * Order matters: the model's opinion is the more specific one, so it is reported first.
     */
    fun reason(classifierScore: Float?, rhythmScore: Float): String = when {
        (classifierScore ?: 0f) >= CLASSIFIER_CONFIDENT && rhythmScore >= RHYTHM_CONFIDENT ->
            "model i rytm oddechu się zgadzają"
        (classifierScore ?: 0f) >= CLASSIFIER_CONFIDENT -> "model rozpoznał chrapanie"
        rhythmScore >= RHYTHM_CONFIDENT -> "rytmiczne bursty jak oddech"
        classifierScore != null -> "model nie był pewny i brak rytmu"
        else -> "brak modelu i brak rytmu"
    }
}
/** What happens to one episode: kept (with the confidence to store) or dropped, and why. */
data class EpisodeVerdict(val keep: Boolean, val confidence: Float, val reason: String)

/**
 * Decides at the end of an episode whether it is worth a row and a clip. The gate catches every
 * rustle, car and cough in a quiet bedroom; saving them all left 50-60 "uncertain" clips a night,
 * which nobody wants to review. Only episodes with some sign of snoring are kept.
 *
 * - The model hears snoring ([MIN_CONFIDENCE] or more): kept and counted.
 * - The room was clearly awake (speech, television, music) and snoring was not clearly there: dropped.
 * - Weak snoring with a breathing rhythm (bursts 2-8 s apart): the rhythm tips it, counted as snoring.
 * - Weak snoring without rhythm ([DROP_BELOW] up to [MIN_CONFIDENCE]): kept as uncertain, to listen to.
 * - Next to no snoring in any window: dropped. A rhythm alone does not save it: a dripping tap
 *   or a ticking radiator is regular too.
 * - Without a model the gate is all there is: kept with [GATE_ONLY_CONFIDENCE].
 */
object EpisodeJudge {
    /** Below this best "Snoring" score the model heard no snoring at all. Speech and rustles score 0.00-0.05. */
    const val DROP_BELOW = 0.10f

    /** An awake sound this likely, and more likely than snoring, means someone is up. */
    const val AWAKE_DOMINANT = 0.5f

    private val MIN_CONFIDENCE get() = DetectionConfig.MIN_CONFIDENCE

    fun judge(hasClassifier: Boolean, snoring: Float, awake: Float, rhythm: Float): EpisodeVerdict = when {
        !hasClassifier -> EpisodeVerdict(true, GATE_ONLY_CONFIDENCE, "brak modelu")
        snoring >= MIN_CONFIDENCE -> EpisodeVerdict(true, snoring, "model rozpoznał chrapanie")
        awake >= AWAKE_DOMINANT && awake > snoring -> EpisodeVerdict(false, snoring, "mowa, telewizja lub muzyka")
        snoring >= DROP_BELOW && rhythm >= DetectionQuality.RHYTHM_CONFIDENT ->
            EpisodeVerdict(true, MIN_CONFIDENCE, "słabe chrapanie w rytmie oddechu")
        snoring >= DROP_BELOW -> EpisodeVerdict(true, snoring, "niepewne")
        else -> EpisodeVerdict(false, snoring, "model nie słyszy chrapania")
    }
}

/**
 * Keeps at most [cap] uncertain episodes per night, the most likely ones. Called as each one is
 * saved: returns the id that should not be kept (the new one itself, or an older, weaker one).
 */
class UncertainBudget(private val cap: Int = DEFAULT_CAP) {
    private val kept = mutableListOf<Pair<String, Float>>()

    fun offer(id: String, confidence: Float): String? {
        kept += id to confidence
        if (kept.size <= cap) return null
        val weakest = kept.minBy { it.second }
        kept.remove(weakest)
        return weakest.first
    }

    companion object {
        const val DEFAULT_CAP = 10
    }
}
