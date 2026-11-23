package app.chrapak.detection

import android.content.Context

/** Sensitivity presets: how aggressively the gate detects activity. */
enum class Sensitivity(val label: String, val marginDb: Float, val absoluteMinDb: Float, val minActiveMs: Long, val minEventMs: Long) {
    LOW("Niska", 10f, -68f, 2000L, 4000L),
    MEDIUM("Średnia", 8f, -70f, 1500L, 3000L),
    HIGH("Wysoka", 5f, -78f, 1000L, 2000L),
}

/** Settings of the detection, kept in plain preferences. */
object DetectionPrefs {
    private const val FILE = "detection"
    private const val SENSITIVITY = "sensitivity"
    private const val CALIBRATED_FLOOR = "calibrated_floor_db"

    fun sensitivity(context: Context): Sensitivity {
        val value = prefs(context).getInt(SENSITIVITY, Sensitivity.MEDIUM.ordinal)
        return Sensitivity.entries.getOrElse(value) { Sensitivity.MEDIUM }
    }

    fun setSensitivity(context: Context, value: Sensitivity) =
        prefs(context).edit().putInt(SENSITIVITY, value.ordinal).apply()

    fun calibratedFloorDb(context: Context): Float? {
        val value = prefs(context).getFloat(CALIBRATED_FLOOR, -100f)
        return if (value == -100f) null else value
    }

    fun setCalibratedFloorDb(context: Context, value: Float) =
        prefs(context).edit().putFloat(CALIBRATED_FLOOR, value).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Build a `DetectionConfig` from the current sensitivity and (optionally) a calibrated floor.
 * Calibration never raises the absolute minimum threshold – only lowers it to be gentler.
 */
fun configFor(sensitivity: Sensitivity, calibratedFloorDb: Float?): DetectionConfig {
    val adjustedMin = calibratedFloorDb?.let { db ->
        // Never raise the threshold; calibrated floor is the room's quiet level.
        // If the room is louder than the preset, keep the preset (safer).
        sensitivity.absoluteMinDb.coerceAtMost(db + 6f)
    } ?: sensitivity.absoluteMinDb

    return DetectionConfig(
        gate = GateConfig(
            marginDb = sensitivity.marginDb,
            absoluteMinDb = adjustedMin,
            minLowBandRatio = 0f,
        ),
        minEventMs = sensitivity.minEventMs,
        minActiveMs = sensitivity.minActiveMs,
    )
}