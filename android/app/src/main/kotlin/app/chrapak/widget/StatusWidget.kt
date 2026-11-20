package app.chrapak.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.widget.RemoteViews
import app.chrapak.R
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.service.ListeningState
import app.chrapak.ui.MainActivity

/** Builds the widget from the app's current state and pushes it to every placed widget. */
object StatusWidget {
    /** Icon tints that read on both the light and the dark widget background. */
    private fun accent(state: WidgetState): Int = when (state) {
        WidgetState.LISTENING -> 0xFF2FA08E.toInt()
        WidgetState.STANDBY -> 0xFF6B67C9.toInt()
        WidgetState.NEEDS_OPEN -> 0xFFC98A1E.toInt()
        WidgetState.OFF -> 0xFF8D909C.toInt()
    }

    fun content(context: Context): WidgetContent {
        val status = ListeningState.status.value
        return WidgetModel.content(
            listening = status.listening,
            checkingNap = status.napChecking,
            standbyRunning = status.armed,
            standbyWanted = AutoListenPrefs.armed(context),
            sinceMs = status.sinceMs,
            events = status.eventCount,
            nowMs = System.currentTimeMillis(),
            scheduleOn = AutoListenPrefs.scheduleOn(context),
            startMin = AutoListenPrefs.startMin(context),
        )
    }

    private fun opensApp(context: Context) = PendingIntent.getActivity(
        context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun wide(context: Context, c: WidgetContent = content(context)): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_wide).apply {
            setTextViewText(R.id.widget_title, c.title)
            setTextViewText(R.id.widget_subtitle, c.subtitle)
            setInt(R.id.widget_icon, "setColorFilter", accent(c.state))
            setOnClickPendingIntent(R.id.widget_root, opensApp(context))
        }

    fun small(context: Context, c: WidgetContent = content(context)): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_small).apply {
            setTextViewText(R.id.widget_short, c.short)
            setInt(R.id.widget_icon, "setColorFilter", accent(c.state))
            setOnClickPendingIntent(R.id.widget_root, opensApp(context))
        }

    /** The system picks the layout by the size the user gave the widget (Android 12+); older ones get the wide one. */
    private fun responsive(context: Context): RemoteViews {
        val c = content(context)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RemoteViews(mapOf(SizeF(40f, 40f) to small(context, c), SizeF(110f, 40f) to wide(context, c)))
        } else {
            wide(context, c)
        }
    }

    /** Re-draws every placed widget. Cheap to call; does nothing when none is placed. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, responsive(context))
    }

    fun placed(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java)).isNotEmpty()

    /** Asks the launcher to place the widget; the user still confirms. False if the launcher cannot. */
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, StatusWidgetProvider::class.java), null, null)
    }
}

class StatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StatusWidget.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        StatusWidget.refresh(context)
    }
}
