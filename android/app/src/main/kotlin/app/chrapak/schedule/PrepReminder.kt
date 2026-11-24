package app.chrapak.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.chrapak.R
import app.chrapak.service.ListeningService
import app.chrapak.ui.MainActivity

/**
 * What the evening "phone on the charger?" reminder says, and the decision of whether to show it
 * at all. Kept separate so the rules can be tested without a device.
 */
data class PrepDecision(val shouldNotify: Boolean, val reason: String)

object PrepReminderRule {
    /**
     * Decide based on what is known at [nowMs]. The reminder is helpful, never a nag: a user who
     * turned off standby gets no reminder, a user who already is listening does not see one, and a
     * user who already has a night in progress does not get a second one.
     */
    fun decide(
        armed: Boolean,
        scheduleOn: Boolean,
        listening: Boolean,
        charging: Boolean,
        hasNightTonight: Boolean,
        reminderShownAtMs: Long?,
        nowMs: Long,
        /** When the evening reminder can be sent at the earliest, in local time. */
        earliestHourOfDay: Int = 20,
        /** The user tapped "nie dziś": do not show anything until this moment. */
        skipUntilMs: Long = 0L,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): PrepDecision {
        if (!armed || !scheduleOn) return PrepDecision(false, "czuwanie wyłączone")
        if (listening) return PrepDecision(false, "już słucha")
        if (hasNightTonight) return PrepDecision(false, "noc już w toku")
        if (charging) return PrepDecision(false, "telefon na ładowarce")
        if (skipUntilMs > nowMs) return PrepDecision(false, "użytkownik wybrał „nie dziś”")
        val hour = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).hour
        if (hour < earliestHourOfDay) return PrepDecision(false, "za wcześnie (przed $earliestHourOfDay:00)")
        // Do not show twice the same evening; the receiver re-arms the alarm after firing.
        if (reminderShownAtMs != null && nowMs - reminderShownAtMs < 6 * 3_600_000L) return PrepDecision(false, "już pokazano")
        return PrepDecision(true, "telefon nie jest na ładowarce, noc się zaraz zacznie")
    }
}

/** Posts the "phone on the charger?" reminder, with quick "yes / not tonight" actions. */
object PrepReminderNotifier {
    private const val CHANNEL_ID = "prep"
    internal const val NOTIFICATION_ID = 5

    const val ACTION_TURN_ON = "app.chrapak.schedule.PREP_TURN_ON"
    const val ACTION_SKIP = "app.chrapak.schedule.PREP_SKIP"

    fun post(context: Context) {
        if (NotificationPrefs.prepOff(context)) return
        PrepReminderPrefs.rememberShown(context, System.currentTimeMillis())
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Przygotowanie do snu", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // A foreground service, not a plain one: on Android 12+ a background service start from a
        // notification action would be refused.
        val turnOn = PendingIntent.getForegroundService(
            context,
            21,
            Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_START_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getBroadcast(
            context,
            22,
            Intent(context, PrepReminderReceiver::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chrapak)
                .setContentTitle(context.getString(R.string.notification_prep_title))
                .setContentText(context.getString(R.string.notification_prep_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "Włącz teraz", turnOn)
                .addAction(0, "Nie dziś", skip)
                .build(),
        )
    }
}

/** Receives the "nie dziś" action and remembers it for the evening so the alarm does not re-fire. */
class PrepReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == PrepReminderNotifier.ACTION_SKIP) {
            // Remember both the dismissal and the moment it happened: the rule suppresses the next
            // six hours and the receiver re-arms the alarm for tomorrow anyway.
            PrepReminderPrefs.skipUntilMs(context, System.currentTimeMillis() + 8 * 3_600_000L)
            context.getSystemService(NotificationManager::class.java).cancel(PrepReminderNotifier.NOTIFICATION_ID)
        }
    }
}

/** When the reminder was last shown, and until when the user asked not to see it again. */
object PrepReminderPrefs {
    private const val FILE = "prep"
    private const val SKIP_UNTIL = "skip_until"
    private const val LAST_SHOWN = "last_shown"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun skipUntilMs(context: Context): Long = prefs(context).getLong(SKIP_UNTIL, 0L)
    fun skipUntilMs(context: Context, value: Long) = prefs(context).edit().putLong(SKIP_UNTIL, value).apply()

    /** Written each time the notification is actually posted, so it is shown at most once an evening. */
    fun lastShownMs(context: Context): Long = prefs(context).getLong(LAST_SHOWN, 0L)
    fun rememberShown(context: Context, value: Long) = prefs(context).edit().putLong(LAST_SHOWN, value).apply()
}