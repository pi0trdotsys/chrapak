package app.chrapak.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.chrapak.R
import app.chrapak.ui.MainActivity

/** The one notification for "automatic listening could not start, open the app". */
object AutoListenNotifier {
    private const val CHANNEL_ID = "alerts"
    private const val NOTIFICATION_ID = 2

    fun notifyNotStarted(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.tile_label), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chrapak)
                .setContentTitle(context.getString(R.string.notification_not_started_title))
                .setContentText(context.getString(R.string.notification_not_started_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
