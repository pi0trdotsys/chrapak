package app.chrapak.schedule

/** What the phone's sensors say right now. `null` means unknown, which counts for nothing either way. */
data class NapSignals(
    /** Proximity sensor covered: the phone is in a pocket or a bag, not on a bedside table. */
    val covered: Boolean? = null,
    /** Lying flat on a surface rather than held, tilted or carried. */
    val flat: Boolean? = null,
    /** Ambient light in lux. */
    val lux: Float? = null,
    val charging: Boolean? = null,
)

data class NapVerdict(val allow: Boolean, val reason: String)

/**
 * Guesses that the user is napping from what the phone can see: the screen has been off for a
 * while, the phone has not been moved, and it is the time of day for a nap. A guess, not a fact:
 * a phone lying on a desk looks the same, so a wrong start is cheap by design. It ends at the
 * next screen-on and is capped, and the audio stays in memory unless it is snoring.
 *
 * Plain logic with the clock passed in, so every case is tested without a phone.
 */
class NapDetector(
    /** The screen must be off, and the phone still, for this long. */
    private val quietMs: Long = DEFAULT_QUIET_MS,
    private val fromMinute: Int = DEFAULT_FROM_MIN,
    private val toMinute: Int = DEFAULT_TO_MIN,
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
    fun shouldStartNap(nowMs: Long, minuteOfDay: Int, inNightWindow: Boolean, capturing: Boolean): Boolean {
        if (capturing || inNightWindow) return false
        if (!ScheduleMath.isInWindow(minuteOfDay, fromMinute, toMinute)) return false
        val quietUntil = quietUntilMs() ?: return false
        return nowMs >= quietUntil
    }

    /** How long the screen has been off and the phone still, as of [nowMs]. */
    fun quietForMs(nowMs: Long): Long = quietUntilMs()?.let { nowMs - (it - quietMs) } ?: 0L

    /**
     * Second opinion once the time-based rule says yes. A covered phone vetoes the nap outright.
     * Otherwise at least two of four weak signs must agree: flat, dark, charging, quiet for twice
     * as long as required. An unknown sensor adds nothing and vetoes nothing.
     */
    fun assess(signals: NapSignals, quietForMs: Long): NapVerdict {
        if (signals.covered == true) return NapVerdict(false, "phone is covered (pocket or bag)")
        val signs = listOfNotNull(
            "flat".takeIf { signals.flat == true },
            "dark".takeIf { (signals.lux ?: Float.MAX_VALUE) <= DARK_LUX },
            "charging".takeIf { signals.charging == true },
            "long quiet".takeIf { quietForMs >= 2 * quietMs },
        )
        return if (signs.size >= REQUIRED_SIGNS) NapVerdict(true, "signs: ${signs.joinToString()}")
        else NapVerdict(false, "only ${signs.size} of $REQUIRED_SIGNS signs${if (signs.isEmpty()) "" else " (${signs.joinToString()})"}")
    }

    companion object {
        const val DARK_LUX = 30f
        const val REQUIRED_SIGNS = 2
        const val DEFAULT_QUIET_MS = 15 * 60_000L
        const val DEFAULT_FROM_MIN = 11 * 60
        const val DEFAULT_TO_MIN = 20 * 60
    }
}
