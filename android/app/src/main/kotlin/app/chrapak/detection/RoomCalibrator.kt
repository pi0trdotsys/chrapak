package app.chrapak.detection

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Measures the room's quiet level by reading the microphone for 10 seconds and computing the 20th
 * percentile of band levels. This floor can be used to lower the absolute minimum threshold
 * (`DetectionConfig.gate.absoluteMinDb`) so the gate detects chrap even when the room is quieter.
 *
 * Calibration never raises the threshold – only lowers it for gentler detection.
 */
object RoomCalibrator {
    private const val SAMPLE_RATE = 16_000
    private const val READ_MS = 10_000L
    private const val FRAME_MS = 32
    private const val HIGH_PASS_HZ = 100f
    private const val LOW_PASS_HZ = 2000f

    private const val TAG = "ChrapakCalibrate"

    /** Calibrate the room and return the measured quiet level in dBFS, or null on error. */
    suspend fun calibrate(context: android.content.Context): Float? {
        val record = openAudioRecord(context, SAMPLE_RATE) ?: return null
        return try {
            record.startRecording()
            val frameSamples = SAMPLE_RATE * FRAME_MS / 1000
            val chunk = ShortArray(SAMPLE_RATE * READ_MS / 1000)
            val frame = ShortArray(frameSamples)
            var frameFill = 0
            var i = 0
            val levels = mutableListOf<Float>()

            // High-pass filter state
            var lpHigh = 0f

            while (i < chunk.size) {
                val read = record.read(chunk, i, chunk.size - i)
                if (read <= 0) break
                i += read
            }

            // Process frames
            var pos = 0
            while (pos < i) {
                val take = minOf(frameSamples, i - pos)
                System.arraycopy(chunk, pos, frame, 0, take)
                frameFill = take

                val bandEnergy = computeBandEnergy(frame, frameFill, lpHigh)
                val levelDb = 20f * log10(sqrt(bandEnergy / frameSamples).coerceAtLeast(1e-9f).toFloat())
                levels.add(levelDb)

                // Update high-pass state for next frame
                lpHigh = 0f

                pos += frameSamples
            }

            if (levels.isEmpty()) return null

            // 20th percentile
            levels.sort()
            val idx = (levels.size * 0.2).toInt().coerceIn(0, levels.size - 1)
            val floor = levels[idx]

            Log.i(TAG, "calibrated floor: ${floor.toFixed(1)} dB (20th percentile, ${levels.size} frames)")
            floor
        } finally {
            record.stop()
            record.release()
        }
    }

    private fun computeBandEnergy(frame: ShortArray, length: Int, lpHigh: Float): Double {
        var bandEnergy = 0.0
        val alphaHigh = onePoleAlpha(HIGH_PASS_HZ)
        val alphaLow = onePoleAlpha(LOW_PASS_HZ)

        var lpBand = 0f
        for (i in 0 until length) {
            val x = frame[i] / 32768f
            lpHigh += alphaHigh * (x - lpHigh)
            val highPassed = x - lpHigh
            lpBand += alphaLow * (highPassed - lpBand)
            bandEnergy += lpBand * lpBand
        }
        return bandEnergy
    }

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val dt = 1f / SAMPLE_RATE
        val rc = 1f / (6.283185307f * cutoffHz)
        return dt / (rc + dt)
    }

    private fun Float.toFixed(precision: Int): String =
        String.format("%.${precision}f", this)

    private fun openAudioRecord(
        context: android.content.Context,
        sampleRate: Int,
    ): AudioRecord? {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
            as AudioManager

        val sources = listOf(
            android.media.MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED",
            android.media.MediaRecorder.AudioSource.MIC to "MIC",
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        )

        for ((src, name) in sources) {
            val record = AudioRecord(
                src,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                sampleRate * 2 * 4,
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "using microphone source: $name")
                return record
            }
            record.release()
        }
        Log.w(TAG, "no working microphone source found")
        return null
    }
}