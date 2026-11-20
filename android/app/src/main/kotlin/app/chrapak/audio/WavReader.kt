package app.chrapak.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads the 16-bit mono WAV files that [WavWriter] produces. */
object WavReader {
    class Wav(val samples: ShortArray, val sampleRate: Int)

    fun read(file: File): Wav {
        val bytes = file.readBytes()
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV file" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = buffer.getInt(24)
        // Our writer always uses a 44 byte header, so the audio starts right after it.
        val count = (bytes.size - 44) / 2
        val samples = ShortArray(count) { buffer.getShort(44 + it * 2) }
        return Wav(samples, sampleRate)
    }
}
