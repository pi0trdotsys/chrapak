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

    fun clearCalibratedFloor(context: Context) =
        prefs(context).edit().remove(CALIBRATED_FLOOR).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Builds the detection settings from the chosen sensitivity and, optionally, the measured quiet level
 * of the room. The absolute threshold sits [CALIBRATION_HEADROOM_DB] above that level, and only when
 * that is lower than the preset: a measurement can make detection more sensitive, never less.
 */
fun configFor(sensitivity: Sensitivity, calibratedFloorDb: Float?): DetectionConfig {
    val adjustedMin = calibratedFloorDb?.let { db ->
        sensitivity.absoluteMinDb.coerceAtMost(maxOf(db + CALIBRATION_HEADROOM_DB, LOWEST_CALIBRATED_DB))
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
/** How far above the measured silence the absolute threshold sits, so the room's own hiss never counts. */
const val CALIBRATION_HEADROOM_DB = 6f

/**
 * However quiet the room, the threshold stays at or above this. A dead-silent bedroom measures around
 * -95 dBFS on the UNPROCESSED microphone; a threshold near it would wake the model for every rustle,
 * costing battery, while snoring loud enough to matter is well above -80 dBFS.
 */
const val LOWEST_CALIBRATED_DB = -80f
