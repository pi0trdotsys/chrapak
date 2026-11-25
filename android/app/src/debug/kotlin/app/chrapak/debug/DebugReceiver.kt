package app.chrapak.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler

/**
 * adb shell am broadcast -n app.chrapak/app.chrapak.debug.DebugReceiver \
 *   -a app.chrapak.debug.SET_WINDOW --es start 23:00 --es end 07:00
 * Only changes the window and re-sets the alarms; standby itself must be turned on in the app.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Notifications on demand, to check how they look without waiting for a night or a Sunday.
        when (intent.action) {
            "app.chrapak.debug.NOTIFY_PREP" -> { app.chrapak.schedule.PrepReminderNotifier.post(context); return }
            "app.chrapak.debug.DROP_BELOW" -> {
                // Applies the new episode rule to recordings made before it: removes episodes the model
                // scored below the given snoring probability, unless they were starred or marked.
                val below = intent.getFloatExtra("confidence", app.chrapak.detection.EpisodeJudge.DROP_BELOW)
                val onlyNight = intent.getStringExtra("night") // optional: limit to one night
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = app.chrapak.data.ChrapakDatabase.get(context)
                        var removed = 0
                        for (night in db.nights().all().filter { onlyNight == null || it.id == onlyNight }) for (e in db.events().forNight(night.id)) {
                            if (e.confidence < below && !e.starred && !e.falsePositive) {
                                e.clipPath?.let { java.io.File(context.filesDir, it).delete() }
                                db.events().delete(e.id)
                                removed++
                            }
                        }
                        Log.i("ChrapakDebug", "removed $removed episodes below $below")
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
            "app.chrapak.debug.SEED_NIGHTS" -> {
                // Two made-up finished nights (yesterday and the night before), no audio: for screenshots
                // and notification checks on an emulator, which has no microphone input to record.
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = app.chrapak.data.ChrapakDatabase.get(context)
                        val now = System.currentTimeMillis()
                        val hour = 3_600_000L
                        for ((n, minutes) in listOf(2 to listOf(4, 6, 3, 8, 5, 9, 7, 6, 4), 1 to listOf(3, 2, 5, 2, 4))) {
                            val start = now - n * 24 * hour - 8 * hour
                            val id = "seed-$n"
                            db.nights().insert(app.chrapak.data.NightEntity(id, start, start + 8 * hour))
                            minutes.forEachIndexed { i, m ->
                                db.events().insert(
                                    app.chrapak.data.SnoreEventEntity("$id-$i", id, start + (i + 1) * 45 * 60_000L, m * 60_000L, -45f, -55f, 0.8f, null),
                                )
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
            "app.chrapak.debug.NOTIFY_SUMMARY", "app.chrapak.debug.NOTIFY_DIGEST" -> {
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        if (intent.action == "app.chrapak.debug.NOTIFY_DIGEST") {
                            app.chrapak.schedule.WeeklyDigestNotifier.post(context)
                        } else {
                            val db = app.chrapak.data.ChrapakDatabase.get(context)
                            val nights = db.nights().all().filter { it.sleepEndMs != null }
                            val stats = nights.map { app.chrapak.report.NightReport.stats(it, db.events().forNight(it.id)) }
                            nights.firstOrNull()?.let { night ->
                                app.chrapak.schedule.SummaryNotifier.post(context, night, stats[0], nap = false, previous = stats.drop(1).firstOrNull { !it.tooShort })
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
        }
        if (intent.action == "app.chrapak.debug.SET_THEME") {
            runCatching { app.chrapak.ui.theme.ThemeChoice.valueOf(intent.getStringExtra("theme").orEmpty()) }
                .onSuccess { app.chrapak.ui.theme.ThemeState.set(context, it) }
            return
        }
        if (intent.action == "app.chrapak.debug.FORCE_NAP") {
            context.startService(
                Intent(context, app.chrapak.service.ListeningService::class.java)
                    .setAction(app.chrapak.service.ListeningService.ACTION_DEBUG_FORCE_NAP)
                    .putExtra("skip_sensors", intent.getBooleanExtra("skip_sensors", false)),
            )
            return
        }
        if (intent.action == "app.chrapak.debug.CONFIRM_NAP") {
            context.startService(Intent(context, app.chrapak.service.ListeningService::class.java).setAction(app.chrapak.service.ListeningService.ACTION_DEBUG_CONFIRM_NAP))
            return
        }
        if (intent.action == "app.chrapak.debug.STOP_LISTENING") {
            app.chrapak.service.ListeningService.stop(context)
            return
        }
        if (intent.action == "app.chrapak.debug.SET_NAP") {
            AutoListenPrefs.setNapQuietMin(context, intent.getIntExtra("quiet", 15))
            Log.i("ChrapakDebug", "nap quiet time set to ${AutoListenPrefs.napQuietMin(context)} min (re-arm to apply)")
            return
        }
        if (intent.action != "app.chrapak.debug.SET_WINDOW") return
        fun minutes(text: String?): Int? = text?.split(":")?.takeIf { it.size == 2 }?.let { it[0].toInt() * 60 + it[1].toInt() }
        val start = minutes(intent.getStringExtra("start")) ?: return
        val end = minutes(intent.getStringExtra("end")) ?: return
        AutoListenPrefs.setWindow(context, start, end)
        AutoListenScheduler.reschedule(context)
        Log.i("ChrapakDebug", "window set to $start..$end min, armed=${AutoListenPrefs.armed(context)}")
    }
}
