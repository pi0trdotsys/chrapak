package app.chrapak.data

/** A night shorter than this with no snoring is a test run or an accidental tap, not a night. */
const val MIN_NIGHT_MS = 60_000L

/**
 * Ends a night at [endMs], or drops it when it is trivial: shorter than [MIN_NIGHT_MS] and without
 * a single episode. A long silent night is kept, because "no snoring" is a result worth reporting.
 */
suspend fun finishNight(nights: NightDao, events: SnoreEventDao, night: NightEntity, endMs: Long) {
    val end = maxOf(night.sleepStartMs, endMs)
    if (end - night.sleepStartMs < MIN_NIGHT_MS && events.count(night.id) == 0) {
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
