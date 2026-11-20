package app.chrapak.tile

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import app.chrapak.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Quick Settings tile: one tap to listen during a nap, one tap to stop. A nap ends by itself after 3 h. */
class ChrapakTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch { ListeningState.status.collect { render(it.listening) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        if (ListeningState.status.value.listening) {
            ListeningService.stop(this)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // No microphone permission yet: open the app so the user can grant it.
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(open)
            return
        }
        ListeningService.start(this, maxMs = ListeningService.NAP_MAX_MS)
    }

    private fun render(listening: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (listening) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Chrapak"
        tile.subtitle = if (listening) "Nasłuch aktywny" else "Drzemka"
        tile.updateTile()
    }
}
