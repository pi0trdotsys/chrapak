package app.chrapak.schedule

/** What the phone's sensors say right now. `null` means unknown, which counts for nothing either way. */
data class NapSignals(
    /** Proximity sensor covered: the phone is in a pocket or a bag, not on a bedside table. */
    val covered: Boolean? = null,
    /** Tiny tremors in the accelerometer: the phone is in a hand, a lap or on something that vibrates. */
    val held: Boolean? = null,
    /** Lying flat on a surface rather than tilted or carried. */
    val flat: Boolean? = null,
    /** Flat with the screen down. Then the light sensor reads zero whatever the room is like. */
    val faceDown: Boolean? = null,
    /** Ambient light in lux. */
    val lux: Float? = null,
    val charging: Boolean? = null,
)

data class NapVerdict(val allow: Boolean, val reason: String)

/**
 * Guesses that the user is napping from what the phone can see: the screen has been off for a
 * while and the phone has not been moved. This works at any time outside the night window, so the
 * evidence required grows when the hour is unusual for a nap. It is only a candidate: a phone on a
 * desk looks the same, so the sounds in the room have the last word (see [NapJudge]).
 *
 * Plain logic with the clock passed in, so every case is tested without a phone.
 */
class NapDetector(
    /** The screen must be off, and the phone still, for this long. */
    private val quietMs: Long = DEFAULT_QUIET_MS,
    /** Hours when a nap is ordinary; outside them one more sign is needed. */
    private val typicalFromMinute: Int = DEFAULT_FROM_MIN,
    private val typicalToMinute: Int = DEFAULT_TO_MIN,
) {
    private var screenOffAtMs: Long? = null
    private var lastMotionAtMs: Long = 0L

    /** Whether the screen is currently off, as far as this detector knows. */
    val screenIsOff: Boolean get() = screenOffAtMs != null

    fun onScreenOff(nowMs: Long) {
        screenOffAtMs = nowMs
    }

    fun onScreenOn() {
        screenOffAtMs = null
    }

    /** Significant motion: the phone was picked up, carried or knocked. Restarts the quiet period. */
    fun onMotion(nowMs: Long) {
        lastMotionAtMs = nowMs
    }

    /** When the quiet period would be over if nothing else happens, or null while the screen is on. */
    fun quietUntilMs(): Long? = screenOffAtMs?.let { maxOf(it, lastMotionAtMs) + quietMs }

    /**
     * Whether a nap should start now. [inNightWindow] and [capturing] suppress it: the nightly
     * window already listens, and a second start would only restart a recording in progress.
     */
    fun shouldStartNap(nowMs: Long, inNightWindow: Boolean, capturing: Boolean): Boolean {
        if (capturing || inNightWindow) return false
        val quietUntil = quietUntilMs() ?: return false
        return nowMs >= quietUntil
    }

    /** How long the screen has been off and the phone still, as of [nowMs]. */
    fun quietForMs(nowMs: Long): Long = quietUntilMs()?.let { nowMs - (it - quietMs) } ?: 0L

    /** Signs needed at this hour: two in ordinary nap hours, three at odd ones. */
    fun requiredSigns(minuteOfDay: Int): Int =
        if (ScheduleMath.isInWindow(minuteOfDay, typicalFromMinute, typicalToMinute)) REQUIRED_SIGNS else REQUIRED_SIGNS + 1

    /**
     * Second opinion once the quiet rule says yes. A covered or a held phone vetoes the nap
     * outright. Otherwise enough weak signs must agree: flat, dark (only for a screen facing up),
     * charging, quiet for twice as long as required. An unknown sensor adds nothing and vetoes nothing.
     */
    fun assess(signals: NapSignals, quietForMs: Long, minuteOfDay: Int): NapVerdict {
        if (signals.covered == true) return NapVerdict(false, "phone is covered (pocket or bag)")
        if (signals.held == true) return NapVerdict(false, "phone is being held or is on something that shakes")
        val signs = listOfNotNull(
            "flat".takeIf { signals.flat == true },
            // Face down, the sensor sees nothing even in a bright room, so darkness proves nothing.
            "dark".takeIf { signals.faceDown != true && (signals.lux ?: Float.MAX_VALUE) <= DARK_LUX },
            "charging".takeIf { signals.charging == true },
            "long quiet".takeIf { quietForMs >= 2 * quietMs },
        )
        val needed = requiredSigns(minuteOfDay)
        return if (signs.size >= needed) NapVerdict(true, "signs: ${signs.joinToString()}")
        else NapVerdict(false, "only ${signs.size} of $needed signs${if (signs.isEmpty()) "" else " (${signs.joinToString()})"}")
    }

    companion object {
        const val DARK_LUX = 30f
        const val REQUIRED_SIGNS = 2
        const val DEFAULT_QUIET_MS = 15 * 60_000L
        const val DEFAULT_FROM_MIN = 11 * 60
        const val DEFAULT_TO_MIN = 20 * 60
    }
}
