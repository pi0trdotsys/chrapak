package app.chrapak.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import app.chrapak.R
import app.chrapak.audio.WavWriter
import app.chrapak.data.ChrapakDatabase
import app.chrapak.data.NightEntity
import app.chrapak.data.SnoreEventEntity
import app.chrapak.detection.DetectedEvent
import app.chrapak.detection.DetectionConfig
import app.chrapak.detection.DetectionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Foreground service that listens through the night. The audio thread only reads the microphone
 * and runs the detection pipeline; clip files and database writes happen on a separate coroutine
 * so disk work never delays the next read.
 */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var stopRequested = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopListening()
            else -> startListening()
        }
        // Never restart blindly: a microphone service may only be started while the app is visible.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (job?.isActive == true) return
        stopRequested = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ListeningState.update { it.copy(error = "Brak zgody na użycie mikrofonu.") }
            stopSelf()
            return
        }
        enterForeground()
        acquireWakeLock()
        ListeningState.update { ListeningStatus(listening = true, sinceMs = System.currentTimeMillis()) }
        job = scope.launch(Dispatchers.IO) {
            try {
                capture()
            } catch (e: Exception) {
                ListeningState.update { it.copy(error = e.message ?: "Błąd nasłuchu.") }
            } finally {
                ListeningState.update { it.copy(listening = false, levelDb = null) }
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun stopListening() {
        // Not a cancel: capture() must drain its queue and close the night before the service stops.
        stopRequested = true
        if (job?.isActive != true) stopSelf()
    }

    @SuppressLint("MissingPermission") // checked in startListening()
    private suspend fun capture() = coroutineScope {
        val db = ChrapakDatabase.get(applicationContext)
        val config = DetectionConfig()
        val sampleRate = config.gate.sampleRate
        val nightId = UUID.randomUUID().toString()
        val startMs = System.currentTimeMillis()
        db.nights().insert(NightEntity(id = nightId, sleepStartMs = startMs, sleepEndMs = null))

        val events = Channel<DetectedEvent>(Channel.UNLIMITED)
        val writer = launch(Dispatchers.IO) {
            for (event in events) {
                val id = UUID.randomUUID().toString()
                val file = File(filesDir, "clips/$nightId/$id.wav")
                WavWriter.write(file, event.pcm, sampleRate)
                db.events().insert(
                    SnoreEventEntity(
                        id = id,
                        nightId = nightId,
                        startMs = event.startMs,
                        durationMs = event.durationMs,
                        peakDb = event.peakDb,
                        avgDb = event.avgDb,
                        confidence = event.confidence,
                        clipPath = file.relativeTo(filesDir).path,
                    ),
                )
                ListeningState.update { it.copy(eventCount = it.eventCount + 1) }
            }
        }

        val pipeline = DetectionPipeline(config, classifier = null, startEpochMs = startMs) { events.trySend(it) }
        val record = createAudioRecord(sampleRate)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        try {
            record.startRecording()
            val chunk = ShortArray(sampleRate / 2) // 0.5 s per read: few wake-ups, low CPU
            var lastUiUpdate = 0L
            while (!stopRequested && isActive) {
                val read = record.read(chunk, 0, chunk.size)
                if (read < 0) error("Odczyt z mikrofonu nie powiódł się (kod $read).")
                if (read == 0) continue
                pipeline.feed(chunk, read)
                if (debuggable) {
                    val d = pipeline.takeDiagnostics()
                    Log.d(
                        TAG,
                        "peak=%.1f dB ratio=%.2f active=%d/%d floor=%.1f dB".format(
                            d.peakDb, d.lowBandRatioAtPeak, d.activeFrames, d.frames, d.noiseFloorDb,
                        ),
                    )
                }
                val now = System.currentTimeMillis()
                if (now - lastUiUpdate >= UI_UPDATE_MS) {
                    lastUiUpdate = now
                    ListeningState.update { it.copy(levelDb = rmsDb(chunk, read)) }
                }
            }
        } finally {
            record.stop()
            record.release()
            withContext(NonCancellable) {
                pipeline.finish()
                events.close()
                writer.join()
                db.nights().finish(nightId, System.currentTimeMillis())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int): AudioRecord {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // UNPROCESSED skips automatic gain control, which would flatten the snore dynamics.
        val source = if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBytes, sampleRate * 2 * 4) // 4 s of slack for the reader
        val record = AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Nie udało się zainicjować mikrofonu." }
        return record
    }

    private fun rmsDb(samples: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val x = samples[i] / 32768.0
            sum += x * x
        }
        return (20.0 * log10(sqrt(sum / length) + 1e-9)).toFloat()
    }

    private fun enterForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause),
            getString(R.string.notification_stop),
            stopIntent,
        ).build()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chrapak:listening").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "ChrapakGate"
        const val ACTION_START = "app.chrapak.action.START"
        const val ACTION_STOP = "app.chrapak.action.STOP"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val UI_UPDATE_MS = 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_STOP))
        }
    }
}
