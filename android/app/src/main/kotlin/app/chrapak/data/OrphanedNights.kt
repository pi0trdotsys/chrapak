package app.chrapak.data

/** Tag on a night that began as a guessed nap. */
const val NAP_TAG = "drzemka"

/** A night shorter than this with no snoring is a test run or an accidental tap, not a night. */
const val MIN_NIGHT_MS = 60_000L

/**
 * Ends a night at [endMs], or drops it when it is trivial: shorter than [MIN_NIGHT_MS] and without
 * a single episode, or a guessed nap that recorded no snoring (the guess may have been wrong, and
 * an empty nap is not worth a row). A long silent night is kept, because "no snoring" is a result
 * worth reporting there.
 */
suspend fun finishNight(nights: NightDao, events: SnoreEventDao, night: NightEntity, endMs: Long) {
    val end = maxOf(night.sleepStartMs, endMs)
    val noEvents = events.count(night.id) == 0
    if (noEvents && (end - night.sleepStartMs < MIN_NIGHT_MS || night.isNap())) {
        nights.delete(night.id)
    } else {
        nights.finish(night.id, end)
    }
}

/**
 * Closes nights left open by a killed service. The true end is unknown, so the night ends at its
 * last stored event, or at its start if it has none: a lower bound, never a made-up later time.
 */
suspend fun closeOrphanedNights(nights: NightDao, events: SnoreEventDao) {
    for (night in nights.open()) {
        finishNight(nights, events, night, events.lastEventEndMs(night.id) ?: night.sleepStartMs)
    }
}

fun NightEntity.isNap(): Boolean = tags.split(',').contains(NAP_TAG)

/** How long the audio of an uncertain episode is kept for review before it is removed. */
const val UNCERTAIN_KEEP_MS = 7 * 24 * 60 * 60_000L

/**
 * Removes uncertain episodes older than [UNCERTAIN_KEEP_MS] that nobody kept or marked: every
 * episode the gate catches is saved, so without this the noise of many nights would fill the phone.
 * Starred clips and anything counted as snoring are never touched.
 */
suspend fun pruneUncertain(events: SnoreEventDao, filesDir: java.io.File, nowMs: Long) {
    for (event in events.uncertainBefore(nowMs - UNCERTAIN_KEEP_MS, app.chrapak.detection.DetectionConfig.MIN_CONFIDENCE)) {
        event.clipPath?.let { java.io.File(filesDir, it).delete() }
        events.delete(event.id)
    }
}
