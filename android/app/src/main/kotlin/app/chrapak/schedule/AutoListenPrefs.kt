package app.chrapak.schedule

import android.content.Context

/** Settings of the automatic listening, kept in plain preferences (a few small values). */
object AutoListenPrefs {
    private const val FILE = "auto_listen"
    private const val ARMED = "armed"
    private const val START = "start_min"
    private const val END = "end_min"
    private const val SCHEDULE_ON = "schedule_on"
    private const val NAP_DETECT = "nap_detect"
    private const val SUMMARY = "morning_summary"
    private const val NAP_QUIET = "nap_quiet_min"

    const val DEFAULT_START_MIN = 23 * 60
    const val DEFAULT_END_MIN = 7 * 60

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Standby is on: the service waits for the window and starts listening by itself. */
    fun armed(context: Context) = prefs(context).getBoolean(ARMED, false)
    fun setArmed(context: Context, value: Boolean) = prefs(context).edit().putBoolean(ARMED, value).apply()

    fun scheduleOn(context: Context) = prefs(context).getBoolean(SCHEDULE_ON, true)
    fun setScheduleOn(context: Context, value: Boolean) = prefs(context).edit().putBoolean(SCHEDULE_ON, value).apply()

    /** A notification with the night's numbers when listening ends. */
    fun summaryOn(context: Context) = prefs(context).getBoolean(SUMMARY, true)
    fun setSummaryOn(context: Context, value: Boolean) = prefs(context).edit().putBoolean(SUMMARY, value).apply()

    /** Guess that the user is napping from the phone's own state (screen off, not moved, daytime). */
    fun napDetect(context: Context) = prefs(context).getBoolean(NAP_DETECT, true)
    fun setNapDetect(context: Context, value: Boolean) = prefs(context).edit().putBoolean(NAP_DETECT, value).apply()

    /** Minutes of a dark, still phone before a nap is assumed. Debug builds can shorten it for tests. */
    fun napQuietMin(context: Context) = prefs(context).getInt(NAP_QUIET, 15)
    fun setNapQuietMin(context: Context, value: Int) = prefs(context).edit().putInt(NAP_QUIET, value).apply()

    fun startMin(context: Context) = prefs(context).getInt(START, DEFAULT_START_MIN)
    fun endMin(context: Context) = prefs(context).getInt(END, DEFAULT_END_MIN)
    fun setWindow(context: Context, startMin: Int, endMin: Int) =
        prefs(context).edit().putInt(START, startMin).putInt(END, endMin).apply()
}
