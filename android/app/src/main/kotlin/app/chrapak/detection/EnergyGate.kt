package app.chrapak.detection

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Settings of the cheap first stage. Everything here runs for every audio frame all night,
 * so it is deliberately a few multiplications per sample and no model.
 */
data class GateConfig(
    val sampleRate: Int = 16_000,
    val frameMs: Int = 32,
    /** Snoring lives mostly between roughly 100 Hz and 2 kHz. */
    val highPassHz: Float = 100f,
    val lowPassHz: Float = 2_000f,
    /** Frequency splitting "low" from "high" in the band, for the optional ratio check below. */
    val lowBandHz: Float = 600f,
    /**
     * Minimum share of band energy below [lowBandHz]. Off by default: measured on a phone, imitated
     * snoring scores 0.15-0.4, no better than broadband noise (about 0.28), because first-order
     * filters are too gentle to separate them. Telling snoring from noise is the classifier's job.
     */
    val minLowBandRatio: Float = 0f,
    /** How far above the room's noise floor a frame must be to count as active. */
    val marginDb: Float = 8f,
    /**
     * Never activate below this absolute level (dBFS), whatever the floor says. The UNPROCESSED
     * microphone is quiet: an empty room sits around -85 dB with spikes to -75 dB, while imitated
     * snoring averages around -64 dB and peaks near -50 dB.
     */
    val absoluteMinDb: Float = -70f,
    /** Frames spent learning the room's silence before any frame can be active. */
    val warmupFrames: Int = 40,
    /** Floor follows a quieter room quickly... */
    val floorFallRate: Float = 0.2f,
    /** ...and a louder one very slowly, so a fan does not read as snoring but a snore does. */
    val floorRiseRate: Float = 0.0004f,
) {
    val frameSamples: Int get() = sampleRate * frameMs / 1000
}

data class GateResult(val levelDb: Float, val active: Boolean, val lowBandRatio: Float)

/** Adaptive band-energy gate. Feed it exactly [GateConfig.frameSamples] samples at a time. */
class EnergyGate(private val config: GateConfig = GateConfig()) {
    private val alphaHigh = onePoleAlpha(config.highPassHz)
    private val alphaLow = onePoleAlpha(config.lowPassHz)
    private val alphaLowBand = onePoleAlpha(config.lowBandHz)

    private var lpHigh = 0f
    private var lpBand = 0f
    private var lpLowBand = 0f
    private var floorDb = 0f
    private var frames = 0

    /** Current estimate of the room's silence level in dBFS. */
    val noiseFloorDb: Float get() = floorDb

    fun process(samples: ShortArray, offset: Int = 0): GateResult {
        val n = config.frameSamples
        var bandEnergy = 0.0
        var lowEnergy = 0.0
        for (i in offset until offset + n) {
            val x = samples[i] / 32768f
            lpHigh += alphaHigh * (x - lpHigh)
            val highPassed = x - lpHigh
            lpBand += alphaLow * (highPassed - lpBand)
            lpLowBand += alphaLowBand * (highPassed - lpLowBand)
            bandEnergy += lpBand * lpBand
            lowEnergy += lpLowBand * lpLowBand
        }
        val levelDb = 20f * log10(sqrt(bandEnergy / n).toFloat() + 1e-9f)
        val ratio = if (bandEnergy > 0.0) (lowEnergy / bandEnergy).toFloat() else 0f
        frames++

        if (frames <= config.warmupFrames) {
            floorDb = if (frames == 1) levelDb else floorDb + 0.1f * (levelDb - floorDb)
            return GateResult(levelDb, active = false, lowBandRatio = ratio)
        }

        val active = levelDb > floorDb + config.marginDb &&
            levelDb > config.absoluteMinDb &&
            ratio >= config.minLowBandRatio
        val rate = if (levelDb < floorDb) config.floorFallRate else config.floorRiseRate
        floorDb += rate * (levelDb - floorDb)
        return GateResult(levelDb, active, ratio)
    }

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val dt = 1f / config.sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        return dt / (rc + dt)
    }
}
