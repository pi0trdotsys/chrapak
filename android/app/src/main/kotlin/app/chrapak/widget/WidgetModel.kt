package app.chrapak.widget

import app.chrapak.schedule.ScheduleMath

/** What the home screen widget is telling the user. */
enum class WidgetState { LISTENING, STANDBY, NEEDS_OPEN, OFF }

/**
 * The words on the widget. [short] is for the 1x1 size, where only one short word fits;
 * [title] and [subtitle] are for 2x1.
 */
data class WidgetContent(val state: WidgetState, val title: String, val subtitle: String, val short: String)

/** Maps the app's state to widget text. Pure, so the wording and the odd cases are tested. */
object WidgetModel {
    fun content(
        listening: Boolean,
        /** A nap was guessed and is being checked; the widget says so instead of claiming a nap. */
        checkingNap: Boolean = false,
        /** The standby service is alive in this process. */
        standbyRunning: Boolean,
        /** The user switched standby on, whether or not the service survived. */
        standbyWanted: Boolean,
        sinceMs: Long?,
        events: Int,
        nowMs: Long,
        scheduleOn: Boolean,
        startMin: Int,
    ): WidgetContent = when {
        listening && checkingNap -> WidgetContent(WidgetState.LISTENING, "Sprawdzam", "czy to drzemka", "Sprawdzam")
        listening -> {
            val elapsed = sinceMs?.let { minutesLabel(nowMs - it) }.orEmpty()
            WidgetContent(
                WidgetState.LISTENING,
                title = "Nasłuch",
                subtitle = listOf(elapsed, episodes(events)).filter { it.isNotEmpty() }.joinToString(" · "),
                short = shortElapsed(sinceMs?.let { nowMs - it } ?: 0L),
            )
        }
        standbyRunning -> WidgetContent(
            WidgetState.STANDBY,
            title = "Czuwam",
            subtitle = if (scheduleOn) "Start o ${ScheduleMath.format(startMin)}" else "Start ręczny",
            short = "Czuwam",
        )
        standbyWanted -> WidgetContent(WidgetState.NEEDS_OPEN, "Czuwanie przerwane", "Otwórz aplikację", "Otwórz")
        else -> WidgetContent(WidgetState.OFF, "Wyłączony", "Dotknij, aby otworzyć", "Wyłączony")
    }

    /** "7 min" or "1 h 23 min": minutes are enough, the widget refreshes about once a minute. */
    fun minutesLabel(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    /** "1:23" for the small widget. */
    fun shortElapsed(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        return "%d:%02d".format(minutes / 60, minutes % 60)
    }

    private fun episodes(n: Int) = when {
        n == 0 -> "cicho"
        n == 1 -> "1 epizod"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n epizody"
        else -> "$n epizodów"
    }
}
