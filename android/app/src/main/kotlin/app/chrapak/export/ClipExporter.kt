package app.chrapak.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import app.chrapak.data.SnoreEventEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Exports a snore clip to a user-selected location via ACTION_CREATE_DOCUMENT.
 * Usage: call export(), then registerForActivityResult in the caller with the returned Intent.
 */
class ClipExporter(private val context: Context) {
    private val TAG = "ChrapakClipExporter"

    fun export(event: SnoreEventEntity, clipFile: File): Intent {
        val destIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "chrapak-${event.id}.wav")
        }
        Log.i(TAG, "export intent created for ${event.id}, source: ${clipFile.absolutePath}")
        return destIntent
    }

    fun copyTo(uri: Uri, clipFile: File): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileInputStream(clipFile).use { fis ->
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fis.channel.transferTo(0, fis.channel.size(), fos.channel)
                    }
                }
            } ?: run {
                Log.e(TAG, "failed to open file descriptor for $uri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "export error: ${e.message}", e)
            false
        }
    }
}