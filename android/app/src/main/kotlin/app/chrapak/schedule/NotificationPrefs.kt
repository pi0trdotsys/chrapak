package app.chrapak.schedule

import android.content.Context

/**
 * Switches for the optional notification channels, kept in their own file so the existing
 * `auto_listen` preferences stay about listening and not about notifications.
 */
object NotificationPrefs {
    private const val FILE = "notifications"
    private const val DIGEST_OFF = "digest_off"
    private const val PREP_OFF = "prep_off"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Weekly digest notification; on unless the user switched it off. */
    fun digestOff(context: Context): Boolean = prefs(context).getBoolean(DIGEST_OFF, false)
    fun setDigestOff(context: Context, off: Boolean) = prefs(context).edit().putBoolean(DIGEST_OFF, off).apply()

    /** Evening "phone on the charger?" reminder, half an hour before the listening window starts. */
    fun prepOff(context: Context): Boolean = prefs(context).getBoolean(PREP_OFF, false)
    fun setPrepOff(context: Context, off: Boolean) = prefs(context).edit().putBoolean(PREP_OFF, off).apply()
}