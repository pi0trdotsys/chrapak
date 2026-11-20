package app.chrapak.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes 16-bit mono PCM as a plain WAV file. Simple and lossless; compression can come later. */
object WavWriter {
    private const val HEADER_BYTES = 44

    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val dataBytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // PCM header size
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
        for (sample in pcm) buffer.putShort(sample)
        RandomAccessFile(file, "rw").use {
            it.setLength(0)
            it.write(buffer.array())
        }
    }
}
