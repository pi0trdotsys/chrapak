package app.chrapak.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
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
import app.chrapak.data.NAP_TAG
import app.chrapak.data.NightEntity
import app.chrapak.data.isNap
import app.chrapak.data.closeOrphanedNights
import app.chrapak.data.finishNight
import app.chrapak.data.SnoreEventEntity
import app.chrapak.data.pruneUncertain
import app.chrapak.detection.DetectedEvent
import app.chrapak.detection.DetectionConfig
import app.chrapak.detection.DetectionPipeline
import app.chrapak.detection.DetectionPrefs
import app.chrapak.detection.RoomCalibrator
import app.chrapak.detection.SnoreClassifier
import app.chrapak.detection.YamnetClassifier
import app.chrapak.detection.configFor
import app.chrapak.schedule.AutoListenNotifier
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler
import app.chrapak.schedule.NapDetector
import app.chrapak.schedule.NapJudge
import app.chrapak.schedule.NapVerdict
import app.chrapak.schedule.NapStage
import app.chrapak.schedule.NapSensors
import app.chrapak.report.NightReport
import app.chrapak.schedule.ScheduleMath
import app.chrapak.schedule.SummaryNotifier
import app.chrapak.ui.MainActivity
import app.chrapak.ui.timeLabel
import app.chrapak.widget.StatusWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private var startedByNap = false
    private var napDetector = NapDetector()
    private var napJudge = NapJudge()
    private var napConfirmed = false

    /** Debug builds only: skip the sensor verdict, because an emulator has no real sensors. */
    private var debugSkipSensors = false
    private var napStartedAtMs = 0L
    private var lastProbeAtMs = 0L
    private var captureStartMs = 0L
    private var plannedEndMs = 0L
    private var lastNotifyAtMs = 0L

    /** After the user stops a window recording by hand, the window must not start it again until it is over. */
    private var suppressWindowUntilMs = 0L
    private var screenReceiver: BroadcastReceiver? = null
    private var motionListener: TriggerEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // The widget only needs to change when something it shows changes, not on every level update.
        scope.launch {
            ListeningState.status
                .map { listOf(it.listening, it.napChecking, it.armed, it.eventCount) }
                .distinctUntilChanged()
                .collect { StatusWidget.refresh(applicationContext) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(AUTO_TAG, "service: ${intent?.action} at ${java.util.Date()}, capturing=${job?.isActive == true}, inWindow=${insideWindow()}")
        when (intent?.action) {
            ACTION_ARM -> arm()
            ACTION_DISARM -> disarm()
            ACTION_STOP -> {
                if (startedByWindow && job?.isActive == true) suppressWindowUntilMs = plannedEndMs
                stopListening()
            }
            // HyperOS may deliver this alarm minutes early, so it only counts once the clock really left the window.
            ACTION_WINDOW_END -> if (startedByWindow && !insideWindow()) stopListening() else if (!foregroundStarted) stopSelf()
            // The power manager can deliver this alarm at the wrong moment, so the clock decides.
            ACTION_WINDOW_START -> startIfWindowOpen()
            ACTION_WATCHDOG -> {
                startIfWindowOpen()
                evaluateNap()
            }
            ACTION_NAP_CHECK -> evaluateNap()
            // Debug builds only: pretend the screen has been off for the whole quiet period, so the sensor
            // verdict, the start, the probation and the clean-up can be tested without waiting or an alarm.
            ACTION_DEBUG_FORCE_NAP -> if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                debugSkipSensors = intent?.getBooleanExtra("skip_sensors", false) == true
                napDetector.onScreenOff(System.currentTimeMillis() - AutoListenPrefs.napQuietMin(this) * 60_000L - 1_000L)
                evaluateNap()
            }
            // Debug builds only: act as if the judge had confirmed the nap, to check the Live Update starts then.
            ACTION_DEBUG_CONFIRM_NAP -> if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && startedByNap && job?.isActive == true) {
                napConfirmed = true
                ListeningState.update { it.copy(napChecking = false) }
                refreshNotification()
            }
            else -> startListening(byWindow = false, maxMs = intent?.getLongExtra(EXTRA_MAX_MS, 0L) ?: 0L)
        }
        // Never restart blindly: a microphone service may only be started while the app is visible.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopNapSensing()
        releaseWakeLock()
        scope.cancel()
        ListeningState.update { it.copy(armed = false, listening = false, levelDb = null) }
        StatusWidget.refresh(applicationContext)
        super.onDestroy()
    }

    /** Standby on: wait quietly, and start at once if the daily window is already open. */
    private fun arm() {
        AutoListenPrefs.setArmed(this, true)
        if (!enterForeground(capturing = job?.isActive == true)) return
        ListeningState.update { it.copy(armed = true) }
        AutoListenScheduler.reschedule(this)
        startNapSensing()
        if (AutoListenPrefs.scheduleOn(this) && insideWindow() && System.currentTimeMillis() >= suppressWindowUntilMs && job?.isActive != true) {
            startListening(byWindow = true, maxMs = 0L)
        }
    }

    /** Starts listening only when standby is on, the window is open and nothing is recording yet. */
    private fun startIfWindowOpen() {
        val shouldListen = AutoListenPrefs.armed(this) && AutoListenPrefs.scheduleOn(this) && insideWindow() &&
            System.currentTimeMillis() >= suppressWindowUntilMs
        if (shouldListen && job?.isActive != true) startListening(byWindow = true, maxMs = 0L)
        else if (!foregroundStarted) stopSelf()
    }

    private fun disarm() {
        AutoListenPrefs.setArmed(this, false)
        AutoListenScheduler.cancel(this)
        stopNapSensing()
        ListeningState.update { it.copy(armed = false) }
        if (job?.isActive == true) {
            if (startedByWindow) stopListening()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startListening(byWindow: Boolean, maxMs: Long, byNap: Boolean = false) {
        if (job?.isActive == true) return
        stopRequested = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ListeningState.update { it.copy(error = "Brak zgody na użycie mikrofonu.") }
            if (!foregroundStarted) stopSelf()
            return
        }
        startedByWindow = byWindow
        startedByNap = byNap
        napStartedAtMs = System.currentTimeMillis()
        lastProbeAtMs = 0L
        napJudge = NapJudge()
        napConfirmed = false
        autoStopAtMs = if (maxMs > 0) System.currentTimeMillis() + maxMs else 0L
        captureStartMs = System.currentTimeMillis()
        plannedEndMs = when {
            byWindow -> ScheduleMath.nextOccurrenceMs(captureStartMs, AutoListenPrefs.endMin(this), ZoneId.systemDefault())
            maxMs > 0 -> captureStartMs + maxMs
            else -> 0L
        }
        lastNotifyAtMs = captureStartMs
        ListeningState.update { it.copy(listening = true, napChecking = byNap, sinceMs = System.currentTimeMillis(), eventCount = 0, error = null) }
        // The notification reads the fields above, so it is built only now.
        if (!enterForeground(capturing = true)) {
            ListeningState.update { it.copy(listening = false) }
            return
        }
        acquireWakeLock()
        job = scope.launch(Dispatchers.IO) {
            try {
                capture()
            } catch (e: Exception) {
                ListeningState.update { it.copy(error = e.message ?: "Błąd nasłuchu.") }
            } finally {
                Log.i(AUTO_TAG, "capture ended at ${java.util.Date()}: stopRequested=$stopRequested pastAutoStop=${pastAutoStop()} windowEnded=${windowEnded()}")
                ListeningState.update { it.copy(listening = false, napChecking = false, levelDb = null) }
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


    // ---- nap detection: screen state, motion and a check alarm ----

    private fun startNapSensing() {
        stopNapSensing()
        if (!AutoListenPrefs.napDetect(this)) return
        napDetector = NapDetector(quietMs = AutoListenPrefs.napQuietMin(this) * 60_000L)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        screenReceiver = receiver
        // The screen may already be off when standby comes on, e.g. after the app restored it.
        if (!getSystemService(PowerManager::class.java).isInteractive) onScreenOff()
    }

    private fun stopNapSensing() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        cancelMotionTrigger()
        AutoListenScheduler.cancelNapCheck(this)
    }

    private fun onScreenOff() {
        napDetector.onScreenOff(System.currentTimeMillis())
        armMotionTrigger()
        scheduleNapCheck()
        Log.i(AUTO_TAG, "nap: screen off, quiet until ${napDetector.quietUntilMs()?.let { java.util.Date(it) }}")
    }

    private fun onScreenOn() {
        napDetector.onScreenOn()
        cancelMotionTrigger()
        AutoListenScheduler.cancelNapCheck(this)
        // A nap ends when the user picks up the phone.
        if (startedByNap && job?.isActive == true) stopListening()
        Log.i(AUTO_TAG, "nap: screen on")
    }

    private fun onMotion() {
        napDetector.onMotion(System.currentTimeMillis())
        if (napDetector.screenIsOff) {
            armMotionTrigger()
            scheduleNapCheck()
        }
        Log.i(AUTO_TAG, "nap: motion, quiet until ${napDetector.quietUntilMs()?.let { java.util.Date(it) }}")
    }

    private fun scheduleNapCheck() {
        napDetector.quietUntilMs()?.let { AutoListenScheduler.scheduleNapCheck(this, it) }
    }

    /** Significant motion is a one-shot, low-power sensor, so it is re-requested after every trigger. */
    private fun armMotionTrigger() {
        cancelMotionTrigger()
        val sensors = getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                onMotion()
            }
        }
        if (sensors.requestTriggerSensor(listener, sensor)) motionListener = listener
    }

    private fun cancelMotionTrigger() {
        val listener = motionListener ?: return
        val sensors = getSystemService(SensorManager::class.java)
        sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { sensors.cancelTriggerSensor(listener, it) }
        motionListener = null
    }

    private fun evaluateNap() {
        if (!AutoListenPrefs.armed(this) || !AutoListenPrefs.napDetect(this)) return
        val now = System.currentTimeMillis()
        val minute = ScheduleMath.minuteOfDay(now, ZoneId.systemDefault())
        if (!napDetector.shouldStartNap(now, inNightWindow = AutoListenPrefs.scheduleOn(this) && insideWindow(), capturing = job?.isActive == true)) return
        // The time rule says yes. Before listening, a second opinion from the sensors: a covered phone,
        // or a phone that is neither flat, dark nor charging, is probably not by a sleeper's side.
        scope.launch {
            val signals = NapSensors.read(applicationContext)
            val verdict = if (debugSkipSensors) NapVerdict(true, "debug: sensors skipped")
            else napDetector.assess(signals, napDetector.quietForMs(System.currentTimeMillis()), minute)
            Log.i(AUTO_TAG, "nap: $signals -> ${verdict.allow} (${verdict.reason})")
            if (verdict.allow) {
                withContext(Dispatchers.Main) {
                    if (job?.isActive != true) startListening(byWindow = false, maxMs = NAP_MAX_MS, byNap = true)
                }
            }
        }
    }

    /** During probation, listen for signs that the user is awake and call the nap off if they are heard. */
    /**
     * While a nap is only a candidate, look at the room every few seconds: awake sounds call it off,
     * snoring or steady breathing confirm it, and the live status starts only after that.
     */
    private fun probeNap(pipeline: DetectionPipeline, classifier: SnoreClassifier?) {
        val c = classifier ?: return
        if (napConfirmed) return
        val now = System.currentTimeMillis()
        if (now - lastProbeAtMs < NapJudge.CHECK_EVERY_MS) return
        lastProbeAtMs = now
        val window = pipeline.latestWindow(c.windowSamples) ?: return
        val evidence = c.napEvidence(window)
        val stage = napJudge.onWindow(evidence, now - napStartedAtMs)
        Log.i(AUTO_TAG, "nap check: awake %.2f snoring %.2f breathing %.2f -> %s".format(evidence.awake, evidence.snoring, evidence.breathing, stage))
        when (stage) {
            NapStage.CONFIRMED -> {
                napConfirmed = true
                ListeningState.update { it.copy(napChecking = false) }
                // Only now does the notification become a Live Update.
                refreshNotification()
            }
            NapStage.ABORTED -> {
                Log.i(AUTO_TAG, "nap: dropped, no sign of a sleeper or the room sounds awake")
                stopRequested = true
            }
            NapStage.CHECKING -> Unit
        }
    }

    /** Back to waiting if standby is on, otherwise the service ends. */
    private fun afterCapture() {
        if (startedByNap) {
            // The nap is over. Without this the detector would start again at once, because the screen is still off.
            startedByNap = false
            napDetector.onScreenOn()
        }
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

    /** The night in one notification, unless the night was dropped as trivial or the user turned it off. */
    private suspend fun postSummary(db: ChrapakDatabase, night: NightEntity) {
        val saved = db.nights().all().firstOrNull { it.id == night.id } ?: return
        val stats = NightReport.stats(saved, db.events().forNight(saved.id))
        SummaryNotifier.post(applicationContext, saved, stats, nap = night.isNap())
    }

    @SuppressLint("MissingPermission") // checked in startListening()
    private suspend fun capture() = coroutineScope {
        val db = ChrapakDatabase.get(applicationContext)
        val sensitivity = DetectionPrefs.sensitivity(this@ListeningService)
        val calibratedFloor = DetectionPrefs.calibratedFloorDb(this@ListeningService)
        val config = configFor(sensitivity, calibratedFloor)
        val sampleRate = config.gate.sampleRate
        val nightId = UUID.randomUUID().toString()
        val startMs = System.currentTimeMillis()
        closeOrphanedNights(db.nights(), db.events())
        val night = NightEntity(id = nightId, sleepStartMs = startMs, sleepEndMs = null, tags = if (startedByNap) NAP_TAG else "")
        // Saved up front: events point at it, and the Tonight and Report screens read it while it is recorded.
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
                // Uncertain episodes keep their audio for review but are not counted as snoring.
                if (event.confidence >= DetectionConfig.MIN_CONFIDENCE) {
                    ListeningState.update { it.copy(eventCount = it.eventCount + 1) }
                    refreshNotification()
                }
            }
        }

        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val classifier = YamnetClassifier.load(applicationContext, logTopClasses = debuggable)
        ListeningState.update { it.copy(classifierActive = classifier != null) }
        val pipeline = DetectionPipeline(config, classifier, startEpochMs = startMs) { event ->
            if (debuggable) {
                Log.d(TAG, "episode: ${event.startMs} ${event.durationMs}ms peak=${event.peakDb}dB conf=${event.confidence}")
            }
            events.trySend(event)
        }

        var record: AudioRecord? = null
        try {
            val chunk = ShortArray(sampleRate * READ_CHUNK_MS / 1000) // one read per chunk: fewer wake-ups, lower CPU
            var lastUiUpdate = 0L
            var readErrors = 0
            while (!stopRequested && isActive && !pastAutoStop() && !windowEnded()) {
                val mic = record ?: run {
                    val (fresh, source) = createAudioRecord(sampleRate)
                    fresh.startRecording()
                    Log.i(TAG, "microphone started: $source")
                    ListeningState.update { it.copy(micSource = source) }
                    record = fresh
                    fresh
                }
                val read = mic.read(chunk, 0, chunk.size)
                if (read < 0) {
                    // The microphone can drop out (another app took it, audio server restarted): reopen it a few times.
                    Log.w(TAG, "microphone read error $read, reopening")
                    runCatching { mic.stop() }
                    mic.release()
                    record = null
                    if (++readErrors > MAX_READ_ERRORS) error("Mikrofon przestał działać (kod $read).")
                    delay(READ_RETRY_DELAY_MS)
                    continue
                }
                if (read == 0) continue // blocking read; zero only on a spurious wake-up
                readErrors = 0
                pipeline.feed(chunk, read)
                if (startedByNap) probeNap(pipeline, classifier)
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
                if (now - lastNotifyAtMs >= NOTIFICATION_REFRESH_MS) {
                    lastNotifyAtMs = now
                    refreshNotification()
                    StatusWidget.refresh(applicationContext)
                }
            }
        } finally {
            record?.let {
                runCatching { it.stop() }
                it.release()
            }
            classifier?.close()
            withContext(NonCancellable) {
                pipeline.finish()
                events.close()
                writer.join()
                finishNight(db.nights(), db.events(), night, System.currentTimeMillis())
                postSummary(db, night)
                runCatching { pruneUncertain(db.events(), filesDir, System.currentTimeMillis()) }
            }
        }
    }

    /**
     * Opens the microphone. UNPROCESSED skips automatic gain control, which would flatten the snore
     * dynamics, but only some phones offer it; the plain MIC and VOICE_RECOGNITION are fallbacks.
     */
    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int): Pair<AudioRecord, String> {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
            add(MediaRecorder.AudioSource.MIC to "MIC")
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
        }
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBytes, sampleRate * 2 * 4) // 4 s of slack for the reader
        for ((source, name) in sources) {
            val record = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to name
            record.release()
        }
        error("Nie udało się zainicjować mikrofonu.")
    }

    private fun rmsDb(samples: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val x = samples[i] / 32768.0
            sum += x * x
        }
        return (20.0 * log10(sqrt(sum / length).coerceAtLeast(1e-9))).toFloat()
    }

    /**
     * Shows the right notification and makes sure the service is in the foreground. Returns false
     * when Android refuses (microphone service started from the background); the user then gets a
     * notification to open the app instead of nothing happening.
     */
    private fun enterForeground(capturing: Boolean): Boolean {
        val notification = buildNotification(capturing)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
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

    /** Refreshes the ongoing notification in place; the elapsed time and episode count move with it. */
    private fun refreshNotification() {
        if (!foregroundStarted || job?.isActive != true) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(capturing = true))
    }

    private fun buildNotification(capturing: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        fun action(label: String, action: String, icon: Int, request: Int): Notification.Action {
            val intent = PendingIntent.getService(this, request, Intent(this, ListeningService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()
        }
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chrapak)
            .setOngoing(true)
            .setContentIntent(open)
        if (capturing && startedByNap && !napConfirmed) {
            // A guess is not shown as a nap: a plain notification, no chip, no progress bar.
            builder.setContentTitle("Sprawdzam, czy to drzemka")
                .setContentText("Słucham kilka minut. Potwierdzę po oddechu lub chrapaniu w cichym pokoju.")
                .addAction(action(getString(R.string.notification_stop), ACTION_STOP, android.R.drawable.ic_media_pause, 0))
        } else if (capturing) {
            val kind = when {
                startedByNap -> "Drzemka w toku"
                startedByWindow -> "Noc w toku"
                else -> getString(R.string.notification_title)
            }
            val events = ListeningState.status.value.eventCount
            val elapsedMs = (System.currentTimeMillis() - captureStartMs).coerceAtLeast(0)
            builder.setContentTitle(kind)
                .setContentText("od ${timeLabel(captureStartMs)} · " + if (events == 0) "bez chrapania" else "$events ${if (events == 1) "epizod" else "epizodów"} chrapania")
                .setWhen(captureStartMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .addAction(action(getString(R.string.notification_stop), ACTION_STOP, android.R.drawable.ic_media_pause, 0))
            // Android 16 "Live Updates": the system may promote this ongoing notification to the status bar
            // as a chip with the elapsed time, and show a progress bar toward the end of the window or nap.
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setRequestPromotedOngoing(true).setShortCriticalText(shortElapsed(elapsedMs))
                val style = Notification.ProgressStyle()
                if (plannedEndMs > captureStartMs) {
                    val totalSeconds = ((plannedEndMs - captureStartMs) / 1000).toInt().coerceAtLeast(1)
                    style.setProgressSegments(listOf(Notification.ProgressStyle.Segment(totalSeconds)))
                        .setProgress((elapsedMs / 1000).toInt().coerceIn(0, totalSeconds))
                } else {
                    style.setProgressIndeterminate(true)
                }
                builder.setStyle(style)
            }
        } else {
            val start = AutoListenPrefs.startMin(this)
            builder.setContentTitle(getString(R.string.notification_standby_title))
                .setContentText(
                    if (AutoListenPrefs.scheduleOn(this)) getString(R.string.notification_standby_text, ScheduleMath.format(start)) else getString(R.string.notification_standby_no_schedule),
                )
                .addAction(action(getString(R.string.notification_standby_off), ACTION_DISARM, android.R.drawable.ic_menu_close_clear_cancel, 1))
        }
        return builder.build()
    }

    /** "1:23" for the status bar chip. */
    private fun shortElapsed(ms: Long): String {
        val minutes = ms / 60_000
        return "%d:%02d".format(minutes / 60, minutes % 60)
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
        const val ACTION_NAP_CHECK = "app.chrapak.action.NAP_CHECK"
        const val ACTION_DEBUG_FORCE_NAP = "app.chrapak.action.DEBUG_FORCE_NAP"
        const val ACTION_DEBUG_CONFIRM_NAP = "app.chrapak.action.DEBUG_CONFIRM_NAP"
        const val EXTRA_MAX_MS = "max_ms"
        /** A Quick Settings nap stops by itself after this long. */
        const val NAP_MAX_MS = 3 * 60 * 60 * 1000L
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val UI_UPDATE_MS = 1_000L
        private const val NOTIFICATION_REFRESH_MS = 60_000L
        private const val READ_CHUNK_MS = 500
        private const val MAX_READ_ERRORS = 5
        private const val READ_RETRY_DELAY_MS = 500L
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
