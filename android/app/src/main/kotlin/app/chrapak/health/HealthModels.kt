package app.chrapak.health

/** Same four stages as `SleepStage` in the web prototype. */
enum class SleepStageType { AWAKE, REM, LIGHT, DEEP }

data class StageSegment(val startMs: Long, val endMs: Long, val stage: SleepStageType) {
    val durationMs: Long get() = endMs - startMs
}

/** One SpO2 (percent) or heart rate (bpm) sample. */
data class VitalPoint(val tsMs: Long, val value: Double, val origin: String)

/** Everything Health Connect knows about a night window, already mapped to our own types. */
data class NightHealth(
    val stages: List<StageSegment> = emptyList(),
    val spo2: List<VitalPoint> = emptyList(),
    val heartRate: List<VitalPoint> = emptyList(),
) {
    val isEmpty: Boolean get() = stages.isEmpty() && spo2.isEmpty() && heartRate.isEmpty()
}
