package app.chrapak.detection

/**
 * Second stage. Receives a window of audio and returns the probability (0..1) that it contains
 * snoring. It is only called while the cheap gate is active, which is what keeps the night cheap.
 * The planned implementation is YAMNet ("Snoring" class); see [GATE_ONLY_CONFIDENCE].
 */
interface SnoreClassifier {
    /** Number of samples (at the pipeline sample rate) one call expects. */
    val windowSamples: Int

    fun score(window: ShortArray): Float
}

/** Confidence reported for events accepted by the gate alone, when no classifier is installed. */
const val GATE_ONLY_CONFIDENCE = 0.5f

data class DetectionConfig(
    val gate: GateConfig = GateConfig(),
    val preRollMs: Int = 5_000,
    /** A breath cycle is 3-5 s and snores sit inside it, so pauses up to this long keep one episode. */
    val mergeGapMs: Long = 6_000,
    val minEventMs: Long = 3_000,
    /** Frames actually active must add up to this much, so sparse noise does not become an episode. */
    val minActiveMs: Long = 1_500,
    /** Audio kept per clip beyond the pre-roll; the event's duration is still recorded in full. */
    val maxClipMs: Int = 60_000,
    val classifyEveryMs: Long = 1_000,
    /**
     * Highest "Snoring" probability over an episode must reach this. Measured with YAMNet on a
     * phone: imitated snoring reached 0.74, while silence, speech and claps stayed at or below
     * 0.02. A low bar costs a few false events in the morning list; a high one would miss real
     * snoring, which is the worse mistake. Tune it on real nights.
     */
    val minConfidence: Float = 0.3f,
)

/**
 * One snoring episode with the audio around it. Levels are dBFS of the 100-2000 Hz band; mapping
 * them to the dB SPL scale shown in the UI needs per-device calibration and happens later.
 */
class DetectedEvent(
    val startMs: Long,
    val durationMs: Long,
    val peakDb: Float,
    val avgDb: Float,
    val confidence: Float,
    val clipStartMs: Long,
    val pcm: ShortArray,
)

/** What the gate saw since the last [DetectionPipeline.takeDiagnostics]; for tuning thresholds. */
data class GateDiagnostics(
    val peakDb: Float,
    val lowBandRatioAtPeak: Float,
    val activeFrames: Int,
    val frames: Int,
    val noiseFloorDb: Float,
)

/**
 * Audio in, snoring events out: cheap gate on every frame, optional classifier only while an
 * episode is open, clip audio kept in memory and handed over once the episode ends.
 * Not thread-safe: call [feed] and [finish] from one thread.
 */
class DetectionPipeline(
    private val config: DetectionConfig = DetectionConfig(),
    private val classifier: SnoreClassifier? = null,
    private val startEpochMs: Long = 0L,
    private val onEvent: (DetectedEvent) -> Unit,
) {
    private val gate = EnergyGate(config.gate)
    private val segmenter = SnoreSegmenter(config.mergeGapMs, config.minEventMs, config.minActiveMs)
    private val sampleRate = config.gate.sampleRate
    private val frameSamples = config.gate.frameSamples
    private val frame = ShortArray(frameSamples)
    private var frameFill = 0
    private var samplesProcessed = 0L

    private val ring = PreRollBuffer(
        maxOf(sampleRate * config.preRollMs / 1000 + frameSamples, classifier?.windowSamples ?: 0),
    )
    private val maxClipSamples = sampleRate * (config.preRollMs + config.maxClipMs) / 1000

    private var clip = ShortArray(0)
    private var clipSize = 0
    private var clipStartMs = 0L

    private var lastClassifyMs = 0L
    private var scoreMax = 0f
    private var scoreCount = 0

    private var diagPeak = Float.NEGATIVE_INFINITY
    private var diagRatio = 0f
    private var diagActive = 0
    private var diagFrames = 0

    /** Summary of the gate's view since the previous call, then resets it. */
    fun takeDiagnostics(): GateDiagnostics {
        val d = GateDiagnostics(diagPeak, diagRatio, diagActive, diagFrames, gate.noiseFloorDb)
        diagPeak = Float.NEGATIVE_INFINITY
        diagRatio = 0f
        diagActive = 0
        diagFrames = 0
        return d
    }

    /** How many times the second stage ran; stays at zero through quiet stretches. */
    var classifierCalls = 0
        private set

    fun feed(samples: ShortArray, length: Int = samples.size) {
        var i = 0
        while (i < length) {
            val take = minOf(frameSamples - frameFill, length - i)
            System.arraycopy(samples, i, frame, frameFill, take)
            frameFill += take
            i += take
            if (frameFill == frameSamples) {
                processFrame()
                frameFill = 0
            }
        }
    }

    /** Ends listening: emits the episode in progress, if it qualifies. */
    fun finish() {
        close(segmenter.flush())
    }

    private fun timeMs(sampleIndex: Long) = startEpochMs + sampleIndex * 1000L / sampleRate

    private fun processFrame() {
        val frameStartMs = timeMs(samplesProcessed)
        val frameMs = config.gate.frameMs
        val result = gate.process(frame)
        diagFrames++
        if (result.active) diagActive++
        if (result.levelDb > diagPeak) {
            diagPeak = result.levelDb
            diagRatio = result.lowBandRatio
        }
        ring.write(frame)
        samplesProcessed += frameSamples

        val wasOpen = segmenter.isOpen
        val segment = segmenter.onFrame(frameStartMs, frameMs, result.active, result.levelDb)

        if (!wasOpen && segmenter.isOpen) openClip(frameStartMs + frameMs)
        else if (wasOpen) appendToClip()

        if (result.active && segmenter.isOpen) classifyIfDue(frameStartMs)
        if (wasOpen && !segmenter.isOpen) close(segment)
    }

    private fun openClip(frameEndMs: Long) {
        val snapshot = ring.latest()
        clip = snapshot.copyOf(maxOf(snapshot.size, 1))
        clipSize = snapshot.size
        clipStartMs = frameEndMs - snapshot.size * 1000L / sampleRate
        lastClassifyMs = frameEndMs
        scoreMax = 0f
        scoreCount = 0
    }

    private fun appendToClip() {
        if (clipSize + frameSamples > maxClipSamples) return
        if (clipSize + frameSamples > clip.size) clip = clip.copyOf(maxOf(clip.size * 2, clipSize + frameSamples))
        System.arraycopy(frame, 0, clip, clipSize, frameSamples)
        clipSize += frameSamples
    }

    private fun classifyIfDue(frameStartMs: Long) {
        val c = classifier ?: return
        if (frameStartMs - lastClassifyMs < config.classifyEveryMs) return
        if (ring.size < c.windowSamples) return
        lastClassifyMs = frameStartMs
        val score = c.score(ring.latest(c.windowSamples))
        classifierCalls++
        scoreCount++
        if (score > scoreMax) scoreMax = score
    }

    private fun close(segment: SnoreSegment?) {
        if (segment != null) {
            val confidence = when {
                classifier == null -> GATE_ONLY_CONFIDENCE
                scoreCount == 0 -> 0f
                else -> scoreMax
            }
            if (classifier == null || confidence >= config.minConfidence) {
                onEvent(
                    DetectedEvent(
                        startMs = segment.startMs,
                        durationMs = segment.durationMs,
                        peakDb = segment.peakDb,
                        avgDb = segment.avgDb,
                        confidence = confidence,
                        clipStartMs = clipStartMs,
                        pcm = clip.copyOf(clipSize),
                    ),
                )
            }
        }
        clip = ShortArray(0)
        clipSize = 0
    }
}
