package app.chrapak.ui.events

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** One shared player: starting a clip stops the previous one. Poll [tick] to update [progress]. */
class ClipPlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var player: MediaPlayer? = null

    fun toggle(id: String, file: File) {
        if (playingId == id) {
            stop()
            return
        }
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.path)
            setOnCompletionListener { stop() }
            prepare()
            start()
        }
        playingId = id
    }

    fun tick() {
        val p = player ?: return
        progress = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1)
    }

    fun stop() {
        player?.release()
        player = null
        playingId = null
        progress = 0f
    }
}
