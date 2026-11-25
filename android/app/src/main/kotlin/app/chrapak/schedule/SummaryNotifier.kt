package app.chrapak.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.chrapak.R
import app.chrapak.data.NightEntity
import app.chrapak.report.NightReport
import app.chrapak.report.NightStats
import app.chrapak.ui.EXTRA_OPEN_RECORDINGS
import app.chrapak.ui.EXTRA_OPEN_REPORT
import app.chrapak.ui.MainActivity
import app.chrapak.ui.durationLabel
import app.chrapak.ui.plural
import app.chrapak.ui.timeLabel

/**
 * The notification you see after listening ends: the score in the title, what happened in one
 * line, and how it compares with the night before. It is silent: the window often ends while the
 * user is still asleep, and a summary is not worth waking up for.
 */
object SummaryNotifier {
    /** New id: a channel's importance cannot change once created, and the old one made a sound. */
    private const val CHANNEL_ID = "night_summary"
    private const val OLD_CHANNEL_ID = "summary"
    private const val NOTIFICATION_ID = 3

    /** Short sessions with no snoring say nothing worth a notification. */
    fun shouldPost(night: NightEntity, stats: NightStats): Boolean =
        stats.count > 0 || stats.nightMs >= 30 * 60_000L

    fun title(night: NightEntity, stats: NightStats, nap: Boolean, endMs: Long): String {
        val span = (if (nap) "Drzemka" else "Noc") + " ${timeLabel(night.sleepStartMs)}–${timeLabel(endMs)}"
        return if (stats.tooShort || stats.count == 0) span else "$span · ${stats.score}/100"
    }

    /** [previous] is the last scored night before this one, for a "better / worse" note; null skips it. */
    fun text(stats: NightStats, previous: NightStats? = null, nap: Boolean = false): String {
        val of = if (nap) "drzemki" else "nocy"
        if (stats.count == 0) {
            val quiet = if (nap) "W tej drzemce nie wykryłem chrapania." else "W tej nocy nie wykryłem chrapania."
            return quiet + suspectNote(stats)
        }
        val parts = mutableListOf(
            "Chrapanie ${durationLabel(stats.snoringMs)} (${stats.percent}% $of)",
            "${stats.count} ${plural(stats.count, "epizod", "epizody", "epizodów")}",
        )
        stats.longest?.let { parts += "najdłuższy ${durationLabel(it.durationMs)} o ${timeLabel(it.startMs)}" }
        val head = parts.joinToString(" · ") + "."
        val label = if (stats.tooShort) "" else " ${NightReport.scoreLabel(stats.score)} chrapanie."
        return head + label + comparison(stats, previous) + suspectNote(stats)
    }

    /** One sentence against the previous scored night, only when both have a score. */
    fun comparison(stats: NightStats, previous: NightStats?): String {
        if (previous == null || stats.tooShort || previous.tooShort) return ""
        val diff = stats.score - previous.score
        return when {
            diff <= -10 -> " Lepiej niż poprzednio (${previous.score}/100)."
            diff >= 10 -> " Gorzej niż poprzednio (${previous.score}/100)."
            else -> " Podobnie jak poprzednio (${previous.score}/100)."
        }
    }

    private fun suspectNote(stats: NightStats) =
        if (stats.suspectCount > 0) " ${stats.suspectCount} ${plural(stats.suspectCount, "nagranie", "nagrania", "nagrań")} do sprawdzenia." else ""

    fun post(context: Context, night: NightEntity, stats: NightStats, nap: Boolean, previous: NightStats? = null) {
        if (!AutoListenPrefs.summaryOn(context) || !shouldPost(night, stats)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Podsumowanie nocy", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_REPORT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val end = night.sleepEndMs ?: System.currentTimeMillis()
        val body = text(stats, previous, nap)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chrapak)
            .setContentTitle(title(night, stats, nap, end))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
        builder.addAction(0, "Raport", open)
        if (stats.count > 0 || stats.suspectCount > 0) {
            val openRecordings = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_RECORDINGS, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Posłuchaj", openRecordings)
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
