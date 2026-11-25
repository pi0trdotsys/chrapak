package app.chrapak.ui

import app.chrapak.health.SleepStageType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val pl: Locale = Locale.forLanguageTag("pl-PL")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", pl)
private val timeSecondsFormat = DateTimeFormatter.ofPattern("HH:mm:ss", pl)
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", pl)

fun timeLabel(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** For axes of very short nights, where minute labels would repeat. */
fun timeLabelSeconds(ms: Long): String = timeSecondsFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun dateLabel(ms: Long): String = dateFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** "1 h 12 min" for long spans, "4 min 20 s" for episodes, "35 s" for short clips. */
fun durationLabel(ms: Long): String {
    val totalSeconds = (ms / 1000.0).roundToInt()
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "$h h $m min"
        m > 0 && s == 0 -> "$m min"
        m > 0 -> "$m min $s s"
        else -> "$s s"
    }
}

fun dbLabel(db: Float): String = "${db.roundToInt()} dBFS"

fun decimal(value: Double): String = String.format(pl, "%.1f", value)

fun stageLabel(stage: SleepStageType): String = when (stage) {
    SleepStageType.AWAKE -> "Czuwanie"
    SleepStageType.REM -> "REM"
    SleepStageType.LIGHT -> "Lekki sen"
    SleepStageType.DEEP -> "Głęboki sen"
}

/** Polish noun form for [n]: `plural(1, "nagranie", "nagrania", "nagrań")`. */
fun plural(n: Int, one: String, few: String, many: String): String = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}
