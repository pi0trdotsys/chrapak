package app.chrapak.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ListeningStatus(
    val listening: Boolean = false,
    val sinceMs: Long? = null,
    val eventCount: Int = 0,
    /** Latest band level in dBFS, for a calm "I can hear you" indicator. Null when idle. */
    val levelDb: Float? = null,
    val error: String? = null,
)

/** Process-wide view of the service for the UI. The service writes, the UI only reads. */
object ListeningState {
    private val mutable = MutableStateFlow(ListeningStatus())
    val status: StateFlow<ListeningStatus> = mutable

    internal fun update(transform: (ListeningStatus) -> ListeningStatus) = mutable.update(transform)
}
