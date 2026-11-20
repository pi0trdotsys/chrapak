package app.chrapak.detection

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * TFLite models with metadata carry their label list as a zip entry appended to the flatbuffer.
 * Reading the index from it, instead of hard-coding "Snoring is class 38", means a different
 * model version cannot silently point at the wrong class.
 */
object YamnetLabels {
    const val LABEL_ENTRY = "yamnet_label_list.txt"

    /** All labels of the model in output order, or null if the file carries no label list. */
    fun read(modelBytes: ByteArray): List<String>? {
        // The zip is appended after the flatbuffer, so a stream must start at its first local header.
        val zipStart = indexOfLocalHeader(modelBytes) ?: return null
        ZipInputStream(ByteArrayInputStream(modelBytes, zipStart, modelBytes.size - zipStart)).use { zip ->
            return try {
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == LABEL_ENTRY }
                    ?.let { zip.readBytes().toString(Charsets.UTF_8).lines().filter { line -> line.isNotBlank() } }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun indexOfLocalHeader(bytes: ByteArray): Int? {
        // Zip local file header signature: "PK\u0003\u0004".
        for (i in 0..bytes.size - 4) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() && bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return null
    }

    fun indexOf(labels: List<String>, label: String): Int? =
        labels.indexOfFirst { it.equals(label, ignoreCase = true) }.takeIf { it >= 0 }
}
