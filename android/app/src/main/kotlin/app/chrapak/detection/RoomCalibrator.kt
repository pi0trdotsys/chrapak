package app.chrapak.detection

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Measures how quiet the bedroom is: listens for a few seconds and takes a low percentile of the
 * band levels, measured by the same [EnergyGate] the night uses, so the number means the same thing.
 * The result can only make detection more sensitive (see [configFor]), never less.
 */
object RoomCalibrator {
    private const val TAG = "ChrapakCalibrate"
    const val LISTEN_MS = 10_000

    /** The quiet level of the room in dBFS, or null when the microphone could not be read. */
    @SuppressLint("MissingPermission") // the caller asks for the microphone first
    suspend fun calibrate(context: Context, listenMs: Int = LISTEN_MS): Float? = withContext(Dispatchers.IO) {
        val config = GateConfig()
        val rate = config.sampleRate
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            if (unprocessed(context)) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC,
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes, rate * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext null
        }
        try {
            record.startRecording()
            val samples = ShortArray(rate * listenMs / 1000)
            var filled = 0
            while (filled < samples.size) {
                ensureActive()
                val read = record.read(samples, filled, samples.size - filled)
                if (read < 0) return@withContext null
                filled += read
            }
            floorOf(samples, filled, config)?.also { Log.i(TAG, "room floor %.1f dB".format(it)) }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * The 20th percentile of frame levels: low enough to ignore a cough or a car, high enough not
     * to be fooled by one dead-silent frame. The first half second is skipped while the filters settle.
     */
    fun floorOf(samples: ShortArray, length: Int, config: GateConfig = GateConfig()): Float? {
        val gate = EnergyGate(config)
        val n = config.frameSamples
        val skip = 500 / config.frameMs
        val levels = mutableListOf<Float>()
        var i = 0
        var frame = 0
        while (i + n <= length) {
            val level = gate.process(samples, i).levelDb
            if (frame++ >= skip) levels += level
            i += n
        }
        if (levels.isEmpty()) return null
        levels.sort()
        return levels[(levels.size * 0.2).toInt().coerceIn(0, levels.size - 1)]
    }

    /** Same choice as the night's recording, so the floor is measured on the same microphone path. */
    private fun unprocessed(context: Context): Boolean =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
}
