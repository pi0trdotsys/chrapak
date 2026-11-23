package app.chrapak.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.chrapak.data.SnoreEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes a clip out of the app: either a copy saved where the user picks (the system file picker),
 * or a share sheet to any app. Nothing is sent anywhere without the user choosing the target.
 */
object ClipExporter {
    /** File name with the time of the episode, e.g. `chrapak-2026-11-23-0312.wav`. */
    fun fileName(event: SnoreEventEntity): String =
        "chrapak-" + SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date(event.startMs)) + ".wav"

    /** Copies [clip] to a document the user created with ACTION_CREATE_DOCUMENT. */
    suspend fun copyTo(context: Context, clip: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                clip.inputStream().use { it.copyTo(out) }
            } != null
        }.getOrDefault(false)
    }

    /** A chooser that sends the clip to an app the user picks; read access is granted to that app only. */
    fun shareIntent(context: Context, event: SnoreEventEntity, clip: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", clip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName(event))
            clipData = ClipData.newRawUri(fileName(event), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Udostępnij nagranie")
    }
}
