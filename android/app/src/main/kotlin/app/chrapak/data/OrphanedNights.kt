package app.chrapak.data

/**
 * Closes nights left open by a killed service. The true end is unknown, so the night ends at its
 * last stored event, or at its start if it has none: a lower bound, never a made-up later time.
 */
suspend fun closeOrphanedNights(nights: NightDao, events: SnoreEventDao) {
    for (night in nights.open()) {
        val end = maxOf(night.sleepStartMs, events.lastEventEndMs(night.id) ?: night.sleepStartMs)
        nights.finish(night.id, end)
    }
}
