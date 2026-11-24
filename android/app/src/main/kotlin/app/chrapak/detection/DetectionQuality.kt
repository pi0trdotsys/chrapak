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