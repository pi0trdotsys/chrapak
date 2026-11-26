package app.chrapak.detection

/**
 * Whether someone is using the phone that listens: screen on and unlocked. Sounds at such a moment
 * come from the phone itself (videos, reels) or from a person who is awake, never from a sleeper.
 * A short grace after the screen goes dark covers the sound that was still playing.
 */
class PhoneUse(private val graceMs: Long = GRACE_MS) {
    private var lastUseMs = Long.MIN_VALUE / 2

    /** Call with the current state; returns true while in use or within the grace after it. */
    fun update(nowMs: Long, screenOn: Boolean, locked: Boolean): Boolean {
        if (screenOn && !locked) lastUseMs = nowMs
        return nowMs - lastUseMs <= graceMs
    }

    companion object {
        const val GRACE_MS = 5_000L
    }
}
