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
import app.chrapak.ui.EXTRA_OPEN_REPORT
import app.chrapak.ui.MainActivity
import app.chrapak.ui.durationLabel
import app.chrapak.ui.timeLabel

/** The notification you see after listening ends: the night in one line, and a tap opens the report. */
object SummaryNotifier {
    private const val CHANNEL_ID = "summary"
    private const val NOTIFICATION_ID = 3

    /** Short sessions with no snoring say nothing worth a notification. */
    fun shouldPost(night: NightEntity, stats: NightStats): Boolean =
        stats.count > 0 || stats.nightMs >= 30 * 60_000L

    fun text(stats: NightStats): String =
        if (stats.count == 0) "W tej nocy nie wykryłem chrapania."
        else "Chrapanie zajęło ${stats.percent}% czasu (${durationLabel(stats.snoringMs)}) · ${stats.count} ${episodes(stats.count)}" +
            if (stats.tooShort) "." else " · wynik ${stats.score}/100 (${NightReport.scoreLabel(stats.score).lowercase()})."

    private fun episodes(n: Int) = when {
        n == 1 -> "epizod"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "epizody"
        else -> "epizodów"
    }

    fun post(context: Context, night: NightEntity, stats: NightStats, nap: Boolean) {
        if (!AutoListenPrefs.summaryOn(context) || !shouldPost(night, stats)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Podsumowanie nocy", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_REPORT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val end = night.sleepEndMs ?: System.currentTimeMillis()
        val title = (if (nap) "Drzemka" else "Noc") + " ${timeLabel(night.sleepStartMs)}–${timeLabel(end)}"
        val body = text(stats)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chrapak)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
