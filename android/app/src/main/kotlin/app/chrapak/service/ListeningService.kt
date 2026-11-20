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
import app.chrapak.data.closeOrphanedNights
import app.chrapak.data.finishNight
import app.chrapak.data.SnoreEventEntity
import app.chrapak.detection.DetectedEvent
import app.chrapak.detection.DetectionConfig
import app.chrapak.detection.DetectionPipeline
import app.chrapak.detection.YamnetClassifier
import app.chrapak.schedule.AutoListenNotifier
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler
import app.chrapak.schedule.ScheduleMath
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
import java.time.ZoneId
import java.util.UUID
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Foreground service with two states. In standby it only waits: the microphone is off and nothing
 * is recorded, but because the service was started while the app was visible, Android lets it open
 * the microphone later, even with the screen off. Listening starts by hand, from a Quick Settings
 * tile, or when the daily window opens, and ends by hand, at the end of the window or after a nap
 * limit. The audio thread only reads the microphone and runs the detection pipeline; clip files
 * and database writes happen on a separate coroutine so disk work never delays the next read.
 */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var autoStopAtMs = 0L
    private var startedByWindow = false
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(AUTO_TAG, "service: ${intent?.action} at ${java.util.Date()}, capturing=${job?.isActive == true}, inWindow=${insideWindow()}")
        when (intent?.action) {
            ACTION_ARM -> arm()
            ACTION_DISARM -> disarm()
            ACTION_STOP -> stopListening()
            // HyperOS may deliver this alarm minutes early, so it only counts once the clock really left the window.
            ACTION_WINDOW_END -> if (startedByWindow && !insideWindow()) stopListening() else if (!foregroundStarted) stopSelf()
            // The power manager can deliver this alarm at the wrong moment, so the clock decides.
            ACTION_WINDOW_START, ACTION_WATCHDOG -> startIfWindowOpen()
            else -> startListening(byWindow = false, maxMs = intent?.getLongExtra(EXTRA_MAX_MS, 0L) ?: 0L)
        }
        // Never restart blindly: a microphone service may only be started while the app is visible.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        ListeningState.update { it.copy(armed = false, listening = false, levelDb = null) }
        super.onDestroy()
    }

    /** Standby on: wait quietly, and start at once if the daily window is already open. */
    private fun arm() {
        AutoListenPrefs.setArmed(this, true)
        if (!enterForeground(capturing = job?.isActive == true)) return
        ListeningState.update { it.copy(armed = true) }
        AutoListenScheduler.reschedule(this)
        if (AutoListenPrefs.scheduleOn(this) && insideWindow() && job?.isActive != true) startListening(byWindow = true, maxMs = 0L)
    }

    /** Starts listening only when standby is on, the window is open and nothing is recording yet. */
    private fun startIfWindowOpen() {
        val shouldListen = AutoListenPrefs.armed(this) && AutoListenPrefs.scheduleOn(this) && insideWindow()
        if (shouldListen && job?.isActive != true) startListening(byWindow = true, maxMs = 0L)
        else if (!foregroundStarted) stopSelf()
    }

    private fun disarm() {
        AutoListenPrefs.setArmed(this, false)
        AutoListenScheduler.cancel(this)
        ListeningState.update { it.copy(armed = false) }
        if (job?.isActive == true) {
            if (startedByWindow) stopListening()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startListening(byWindow: Boolean, maxMs: Long) {
        if (job?.isActive == true) return
        stopRequested = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ListeningState.update { it.copy(error = "Brak zgody na użycie mikrofonu.") }
            if (!foregroundStarted) stopSelf()
            return
        }
        if (!enterForeground(capturing = true)) return
        acquireWakeLock()
        startedByWindow = byWindow
        autoStopAtMs = if (maxMs > 0) System.currentTimeMillis() + maxMs else 0L
        ListeningState.update { it.copy(listening = true, sinceMs = System.currentTimeMillis(), eventCount = 0, error = null) }
        job = scope.launch(Dispatchers.IO) {
            try {
                capture()
            } catch (e: Exception) {
                ListeningState.update { it.copy(error = e.message ?: "Błąd nasłuchu.") }
            } finally {
                Log.i(AUTO_TAG, "capture ended at ${java.util.Date()}: stopRequested=$stopRequested pastAutoStop=${pastAutoStop()} windowEnded=${windowEnded()}")
                ListeningState.update { it.copy(listening = false, levelDb = null) }
                releaseWakeLock()
                afterCapture()
            }
        }
    }

    private fun stopListening() {
        // Not a cancel: capture() must drain its queue and close the night before listening ends.
        stopRequested = true
        if (job?.isActive != true) afterCapture()
    }

    /** Back to waiting if standby is on, otherwise the service ends. */
    private fun afterCapture() {
        if (AutoListenPrefs.armed(this)) {
            enterForeground(capturing = false)
            AutoListenScheduler.reschedule(this)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pastAutoStop(): Boolean = autoStopAtMs != 0L && System.currentTimeMillis() >= autoStopAtMs

    private fun insideWindow(): Boolean =
        ScheduleMath.isInWindow(
            ScheduleMath.minuteOfDay(System.currentTimeMillis(), ZoneId.systemDefault()),
            AutoListenPrefs.startMin(this),
            AutoListenPrefs.endMin(this),
        )

    /**
     * Listening that began with the daily window ends with it. The loop checks the clock itself
     * instead of trusting the end alarm alone, which the phone's power manager may move.
     */
    private fun windowEnded(): Boolean = startedByWindow && AutoListenPrefs.scheduleOn(this) && !insideWindow()

    @SuppressLint("MissingPermission") // checked in startListening()
    private suspend fun capture() = coroutineScope {
        val db = ChrapakDatabase.get(applicationContext)
        val config = DetectionConfig()
        val sampleRate = config.gate.sampleRate
        val nightId = UUID.randomUUID().toString()
        val startMs = System.currentTimeMillis()
        closeOrphanedNights(db.nights(), db.events())
        val night = NightEntity(id = nightId, sleepStartMs = startMs, sleepEndMs = null)
        db.nights().insert(night)

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

        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val classifier = YamnetClassifier.load(applicationContext, logTopClasses = debuggable)
        ListeningState.update { it.copy(classifierActive = classifier != null) }
        val pipeline = DetectionPipeline(config, classifier, startEpochMs = startMs) { events.trySend(it) }
        val record = createAudioRecord(sampleRate)
        try {
            record.startRecording()
            val chunk = ShortArray(sampleRate * READ_CHUNK_MS / 1000) // one read per chunk: fewer wake-ups, lower CPU
            var lastUiUpdate = 0L
            while (!stopRequested && isActive && !pastAutoStop() && !windowEnded()) {
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
            classifier?.close()
            withContext(NonCancellable) {
                pipeline.finish()
                events.close()
                writer.join()
                finishNight(db.nights(), db.events(), night, System.currentTimeMillis())
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

    /**
     * Shows the right notification and makes sure the service is in the foreground. Returns false
     * when Android refuses (microphone service started from the background); the user then gets a
     * notification to open the app instead of nothing happening.
     */
    private fun enterForeground(capturing: Boolean): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        fun action(label: String, action: String, icon: Int, request: Int): Notification.Action {
            val intent = PendingIntent.getService(this, request, Intent(this, ListeningService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
        if (capturing) {
            builder.setContentTitle(getString(R.string.notification_title))
                .addAction(action(getString(R.string.notification_stop), ACTION_STOP, android.R.drawable.ic_media_pause, 0))
        } else {
            val start = AutoListenPrefs.startMin(this)
            builder.setContentTitle(getString(R.string.notification_standby_title))
                .setContentText(
                    if (AutoListenPrefs.scheduleOn(this)) getString(R.string.notification_standby_text, ScheduleMath.format(start)) else getString(R.string.notification_standby_no_schedule),
                )
                .addAction(action(getString(R.string.notification_standby_off), ACTION_DISARM, android.R.drawable.ic_menu_close_clear_cancel, 1))
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, builder.build())
            }
            foregroundStarted = true
            true
        } catch (e: Exception) {
            // Typically ForegroundServiceStartNotAllowedException: the system does not allow a microphone
            // service to start from the background. Tell the user instead of failing silently.
            Log.w(TAG, "cannot enter the foreground: ${e.message}")
            AutoListenNotifier.notifyNotStarted(this)
            ListeningState.update { it.copy(error = "Android nie pozwolił włączyć nasłuchu w tle.") }
            stopSelf()
            false
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
        private const val AUTO_TAG = "ChrapakAuto"
        const val ACTION_START = "app.chrapak.action.START"
        const val ACTION_STOP = "app.chrapak.action.STOP"
        const val ACTION_ARM = "app.chrapak.action.ARM"
        const val ACTION_DISARM = "app.chrapak.action.DISARM"
        const val ACTION_WINDOW_START = "app.chrapak.action.WINDOW_START"
        const val ACTION_WINDOW_END = "app.chrapak.action.WINDOW_END"
        const val ACTION_WATCHDOG = "app.chrapak.action.WATCHDOG"
        const val EXTRA_MAX_MS = "max_ms"
        /** A Quick Settings nap stops by itself after this long. */
        const val NAP_MAX_MS = 3 * 60 * 60 * 1000L
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val UI_UPDATE_MS = 1_000L
        private const val READ_CHUNK_MS = 500
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        fun start(context: Context, maxMs: Long = 0L) {
            context.startForegroundService(
                Intent(context, ListeningService::class.java).setAction(ACTION_START).putExtra(EXTRA_MAX_MS, maxMs),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_STOP))
        }

        /** Turn standby on. Call it while the app is visible, because that is what lets the service use the microphone later. */
        fun arm(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ACTION_ARM))
        }

        fun disarm(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_DISARM))
        }
    }
}
