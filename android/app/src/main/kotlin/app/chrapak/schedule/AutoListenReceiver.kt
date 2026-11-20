package app.chrapak.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState

/** Receives the window alarms and the events after which the alarms must be set again. */
class AutoListenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ChrapakAuto", "receiver: ${intent.action} at ${java.util.Date()}")
        when (intent.action) {
            ListeningService.ACTION_WINDOW_START -> {
                AutoListenScheduler.reschedule(context)
                // A microphone service can only be started from the background if it is already running, so
                // when this fails the service reports it and the user gets a notification.
                runCatching {
                    context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_WINDOW_START))
                }.onFailure { AutoListenNotifier.notifyNotStarted(context) }
            }
            ListeningService.ACTION_WATCHDOG -> {
                AutoListenScheduler.reschedule(context)
                if (!AutoListenPrefs.armed(context) || !AutoListenPrefs.scheduleOn(context)) return
                val windowOpen = ScheduleMath.isInWindow(
                    ScheduleMath.minuteOfDay(System.currentTimeMillis(), java.time.ZoneId.systemDefault()),
                    AutoListenPrefs.startMin(context),
                    AutoListenPrefs.endMin(context),
                )
                // Standby is only alive if the service runs; a dead one cannot start the microphone from here.
                if (windowOpen && !ListeningState.status.value.armed) {
                    AutoListenNotifier.notifyNotStarted(context)
                } else if (windowOpen) {
                    runCatching {
                        context.startService(Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_WATCHDOG))
                    }
                }
            }
            ListeningService.ACTION_NAP_CHECK -> {
                // Only a running standby service can start the microphone; with none, there is nothing to ask.
                if (ListeningState.status.value.armed) {
                    runCatching {
                        context.startService(Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_NAP_CHECK))
                    }
                }
            }
            ListeningService.ACTION_WINDOW_END -> {
                AutoListenScheduler.reschedule(context)
                // Only worth delivering to a service that is already running; ignore the refusal otherwise.
                runCatching {
                    context.startService(Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_WINDOW_END))
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                app.chrapak.widget.StatusWidget.refresh(context)
                if (AutoListenPrefs.armed(context)) {
                    AutoListenScheduler.reschedule(context)
                    // After a restart or an update the standby service is gone and cannot be started from here.
                    if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) {
                        AutoListenNotifier.notifyNotStarted(context)
                    }
                }
            }
        }
    }
}
