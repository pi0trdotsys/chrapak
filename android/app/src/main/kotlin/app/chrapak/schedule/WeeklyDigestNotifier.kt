package app.chrapak.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.chrapak.R
import app.chrapak.data.ChrapakDatabase
import app.chrapak.ui.EXTRA_OPEN_REPORT
import app.chrapak.ui.MainActivity

/**
 * Sunday-morning summary of the last week. Reads from the database (so it can be triggered from a
 * receiver), builds the digest, and shows or drops the notification.
 */
object WeeklyDigestNotifier {
    const val CHANNEL_ID = "digest"
    private const val NOTIFICATION_ID = 4

    /** Decide if there is anything to show. Cheap: looks only at the database count. */
    suspend fun shouldPost(context: Context): Boolean = !NotificationPrefs.digestOff(context) &&
        ChrapakDatabase.get(context).nights().all().any { it.sleepEndMs != null }

    /** Read, build, post. Returns true when something was shown. */
    suspend fun post(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!shouldPost(context)) return false
        val db = ChrapakDatabase.get(context)
        val nights = db.nights().all()
        val eventsByNight = nights.associate { it.id to db.events().forNight(it.id) }
        val digest = WeeklyDigestBuilder.build(nights, eventsByNight, nowMs)
        if (digest.isEmpty) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Tygodniowe podsumowanie", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_REPORT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = WeeklyDigestBuilder.line(digest)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chrapak)
                .setContentTitle(context.getString(R.string.notification_digest_title))
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }
}