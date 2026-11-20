package app.chrapak.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler

/**
 * adb shell am broadcast -n app.chrapak/app.chrapak.debug.DebugReceiver \
 *   -a app.chrapak.debug.SET_WINDOW --es start 23:00 --es end 07:00
 * Only changes the window and re-sets the alarms; standby itself must be turned on in the app.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.chrapak.debug.SET_WINDOW") return
        fun minutes(text: String?): Int? = text?.split(":")?.takeIf { it.size == 2 }?.let { it[0].toInt() * 60 + it[1].toInt() }
        val start = minutes(intent.getStringExtra("start")) ?: return
        val end = minutes(intent.getStringExtra("end")) ?: return
        AutoListenPrefs.setWindow(context, start, end)
        AutoListenScheduler.reschedule(context)
        Log.i("ChrapakDebug", "window set to $start..$end min, armed=${AutoListenPrefs.armed(context)}")
    }
}
