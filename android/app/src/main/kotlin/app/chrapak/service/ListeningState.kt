package app.chrapak.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ListeningStatus(
    val listening: Boolean = false,
    /** A nap was guessed and is being checked against the sounds in the room; not yet a confirmed nap. */
    val napChecking: Boolean = false,
    /** Standby is on: the service is waiting for the daily window and will start listening by itself. */
    val armed: Boolean = false,
    val sinceMs: Long? = null,
    val eventCount: Int = 0,
    /** Latest band level in dBFS, for a calm "I can hear you" indicator. Null when idle. */
    val levelDb: Float? = null,
    /** False when the YAMNet model is missing and events are accepted on the gate alone. */
    val classifierActive: Boolean = false,
    val error: String? = null,
    /** Microphone source in use (UNPROCESSED, MIC, VOICE_RECOGNITION), for diagnostics. */
    val micSource: String? = null,
)

/** Process-wide view of the service for the UI. The service writes, the UI only reads. */
object ListeningState {
    private val mutable = MutableStateFlow(ListeningStatus())
    val status: StateFlow<ListeningStatus> = mutable

    internal fun update(transform: (ListeningStatus) -> ListeningStatus) = mutable.update(transform)
}
