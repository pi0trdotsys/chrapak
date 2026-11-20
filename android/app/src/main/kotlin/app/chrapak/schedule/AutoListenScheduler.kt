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

    /** How often the standby service double-checks that a window which is already open has started. */
    private const val WATCHDOG_MS = 5 * 60_000L

    private const val TAG = "ChrapakAuto"

    fun reschedule(context: Context) {
        cancel(context)
        if (!AutoListenPrefs.armed(context) || !AutoListenPrefs.scheduleOn(context)) {
            Log.i(TAG, "reschedule: nothing to set (armed=${AutoListenPrefs.armed(context)}, schedule=${AutoListenPrefs.scheduleOn(context)})")
            return
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val start = ScheduleMath.nextOccurrenceMs(now, AutoListenPrefs.startMin(context), zone)
        val end = ScheduleMath.nextOccurrenceMs(now, AutoListenPrefs.endMin(context), zone)
        Log.i(TAG, "reschedule: start ${java.util.Date(start)}, end ${java.util.Date(end)}")
        set(context, start, ListeningService.ACTION_WINDOW_START, REQ_START)
        set(context, end, ListeningService.ACTION_WINDOW_END, REQ_END)
        // Inexact on purpose: this is only a safety net for alarms the phone's power manager moved or dropped.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + WATCHDOG_MS, pending(context, ListeningService.ACTION_WATCHDOG, REQ_WATCHDOG))
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
    }

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
