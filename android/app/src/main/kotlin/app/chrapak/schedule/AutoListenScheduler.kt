package app.chrapak.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.chrapak.service.ListeningService
import android.util.Log
import java.time.ZoneId

/**
 * Sets the two alarms of the daily window (start and end). Both are re-set every time one fires,
 * so a changed clock, timezone or window is picked up at the next boundary.
 */
object AutoListenScheduler {
    private const val REQ_START = 11
    private const val REQ_END = 12
    private const val REQ_WATCHDOG = 13
    private const val REQ_NAP = 14
    private const val REQ_DIGEST = 15
    private const val REQ_PREP = 16

    /** How often the standby service double-checks that a window which is already open has started. */
    private const val WATCHDOG_MS = 5 * 60_000L

    /** Minute of day for the Sunday weekly digest: 10:00 local. */
    const val DIGEST_MIN = 10 * 60
    /** Minute of day for the evening "phone on the charger?" reminder: 22:30 local. */
    const val PREP_MIN = 22 * 60 + 30
    /** java.time.DayOfWeek.SUNDAY == 7. Not Calendar.SUNDAY, which is 1 (and means Monday there). */
    const val DIGEST_DAY_OF_WEEK = 7

    private const val TAG = "ChrapakAuto"

    fun reschedule(context: Context) {
        cancel(context)
        scheduleWindow(context)
        scheduleDigest(context)
        schedulePrep(context)
    }

    /** Start, end and the watchdog of the nightly window. Only when standby is on. */
    fun scheduleWindow(context: Context) {
        if (!AutoListenPrefs.armed(context) || !AutoListenPrefs.scheduleOn(context)) {
            Log.i(TAG, "window: nothing to set (armed=${AutoListenPrefs.armed(context)}, schedule=${AutoListenPrefs.scheduleOn(context)})")
            return
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val start = ScheduleMath.nextOccurrenceMs(now, AutoListenPrefs.startMin(context), zone)
        val end = ScheduleMath.nextOccurrenceMs(now, AutoListenPrefs.endMin(context), zone)
        Log.i(TAG, "window: start ${java.util.Date(start)}, end ${java.util.Date(end)}")
        set(context, start, ListeningService.ACTION_WINDOW_START, REQ_START)
        set(context, end, ListeningService.ACTION_WINDOW_END, REQ_END)
        // Inexact on purpose: this is only a safety net for alarms the phone's power manager moved or dropped.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + WATCHDOG_MS, pending(context, ListeningService.ACTION_WATCHDOG, REQ_WATCHDOG))
    }

    /** Sunday digest. Independent of standby: the week's numbers make sense without any night recording. */
    fun scheduleDigest(context: Context) {
        if (NotificationPrefs.digestOff(context)) return
        val at = ScheduleMath.nextWeeklyOccurrenceMs(System.currentTimeMillis(), DIGEST_MIN, DIGEST_DAY_OF_WEEK, ZoneId.systemDefault())
        Log.i(TAG, "digest: ${java.util.Date(at)}")
        set(context, at, ACTION_DIGEST, REQ_DIGEST)
    }

    /** Evening reminder. Only when there is a window it could be reminding about. */
    fun schedulePrep(context: Context) {
        if (NotificationPrefs.prepOff(context)) return
        if (!AutoListenPrefs.armed(context) || !AutoListenPrefs.scheduleOn(context)) return
        val at = ScheduleMath.nextOccurrenceMs(System.currentTimeMillis(), PREP_MIN, ZoneId.systemDefault())
        Log.i(TAG, "prep: ${java.util.Date(at)}")
        set(context, at, ACTION_PREP, REQ_PREP)
    }

    /** One alarm for the moment the quiet period would be over; replaced whenever the phone moves. */
    fun scheduleNapCheck(context: Context, atMs: Long) {
        set(context, atMs, ListeningService.ACTION_NAP_CHECK, REQ_NAP)
    }

    fun cancelNapCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, ListeningService.ACTION_NAP_CHECK, REQ_NAP))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(context, ListeningService.ACTION_WINDOW_START, REQ_START))
        alarms.cancel(pending(context, ListeningService.ACTION_WINDOW_END, REQ_END))
        alarms.cancel(pending(context, ListeningService.ACTION_WATCHDOG, REQ_WATCHDOG))
        alarms.cancel(pending(context, ACTION_DIGEST, REQ_DIGEST))
        alarms.cancel(pending(context, ACTION_PREP, REQ_PREP))
    }

    const val ACTION_DIGEST = "app.chrapak.action.DIGEST"
    const val ACTION_PREP = "app.chrapak.action.PREP"

    private fun set(context: Context, atMs: Long, action: String, request: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, action, request)
        // Exact when allowed (USE_EXACT_ALARM), otherwise a few minutes late is still fine for a sleep window.
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
    }

    private fun pending(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, AutoListenReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
