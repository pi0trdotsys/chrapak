package app.chrapak.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.NightHealth
import app.chrapak.health.SleepStageType
import app.chrapak.health.VitalPoint
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import app.chrapak.ui.timeLabelSeconds
import kotlin.math.abs

/** Episodes at or above this band level (dBFS) are drawn in amber as "loud". */
private const val LOUD_DB = -55f
private const val QUIET_FLOOR_DB = -75f
private const val LOUD_CEILING_DB = -35f

private val STAGE_ROW = mapOf(SleepStageType.AWAKE to 0, SleepStageType.REM to 1, SleepStageType.LIGHT to 2, SleepStageType.DEEP to 3)

/**
 * Four tracks on one shared time axis: sleep stages, snoring episodes, SpO2 and heart rate. The
 * health tracks stay empty (and are labelled so) when Health Connect has no data for the night.
 */
@Composable
fun Timeline(
    startMs: Long,
    endMs: Long,
    events: List<SnoreEventEntity>,
    health: NightHealth,
    onEventTap: (SnoreEventEntity) -> Unit,
    modifier: Modifier = Modifier,
    /** False when Health Connect is not set up; only then does the chart ask to connect it. */
    connected: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Palette.mutedForeground, fontSize = 10.sp)
    val span = (endMs - startMs).coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(events, startMs, endMs) {
                detectTapGestures { tap ->
                    val padding = 8.dp.toPx()
                    val width = size.width - 2 * padding
                    val hit = events.minByOrNull { abs(padding + (it.startMs - startMs).toFloat() / span * width - tap.x) }
                    if (hit != null && abs(padding + (hit.startMs - startMs).toFloat() / span * width - tap.x) <= 28.dp.toPx()) {
                        onEventTap(hit)
                    }
                }
            },
    ) {
        val padding = 8.dp.toPx()
        val width = size.width - 2 * padding
        val axisHeight = 20.dp.toPx()
        val usable = size.height - axisHeight
        fun x(ms: Long) = padding + (ms - startMs).coerceIn(0, span).toFloat() / span * width

        // Lanes as fractions of the usable height.
        val hypno = 0f to 0.27f
        val snore = 0.31f to 0.58f
        val spo2 = 0.62f to 0.79f
        val heart = 0.83f to 1f
        fun lane(l: Pair<Float, Float>, f: Float) = usable * (l.first + (l.second - l.first) * f)

        // Grid and time labels.
        for (i in 0..5) {
            val gx = padding + width * i / 5f
            drawLine(Palette.border, Offset(gx, 0f), Offset(gx, usable), strokeWidth = 1f)
            val tick = startMs + span * i / 5
            val text = if (span < 10 * 60_000L) timeLabelSeconds(tick) else timeLabel(tick)
            val layout = measurer.measure(text, labelStyle)
            val tx = (gx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, usable + 4.dp.toPx()))
        }
        for (l in listOf(hypno, snore, spo2)) {
            drawLine(Palette.border, Offset(padding, usable * l.second + 3.dp.toPx()), Offset(padding + width, usable * l.second + 3.dp.toPx()), strokeWidth = 1f)
        }

        drawStages(health, ::x, hypno, usable)
        drawSnore(events, ::x, snore, usable)
        drawVital(health.spo2, 85.0, 100.0, ::x, spo2, usable, Palette.teal, 2.5f, dipBelow = 92.0)
        drawVital(health.heartRate, health.heartRate.minOfOrNull { it.value } ?: 50.0, (health.heartRate.maxOfOrNull { it.value } ?: 80.0).coerceAtLeast((health.heartRate.minOfOrNull { it.value } ?: 50.0) + 10), ::x, heart, usable, Palette.heart, 1.8f)

        if (health.isEmpty && !connected) {
            val layout = measurer.measure("Połącz Health Connect w Ustawieniach, aby zobaczyć sen i tętno.", labelStyle)
            drawText(layout, topLeft = Offset(padding, lane(hypno, 0.4f)))
        }
    }
}

private fun DrawScope.drawStages(health: NightHealth, x: (Long) -> Float, lane: Pair<Float, Float>, usable: Float) {
    val top = usable * lane.first
    val rowHeight = usable * (lane.second - lane.first) / 4f
    for (segment in health.stages) {
        val row = STAGE_ROW.getValue(segment.stage)
        val color = when (segment.stage) {
            SleepStageType.AWAKE -> Palette.sleepAwake
            SleepStageType.REM -> Palette.primary
            SleepStageType.LIGHT -> Palette.sleepLight
            SleepStageType.DEEP -> Palette.sleepDeep
        }
        val left = x(segment.startMs)
        drawRoundRect(
            color,
            topLeft = Offset(left, top + row * rowHeight + 1.dp.toPx()),
            size = Size((x(segment.endMs) - left).coerceAtLeast(2f), rowHeight - 3.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

private fun DrawScope.drawSnore(events: List<SnoreEventEntity>, x: (Long) -> Float, lane: Pair<Float, Float>, usable: Float) {
    val top = usable * lane.first
    val bottom = usable * lane.second
    val laneHeight = bottom - top
    for (event in events.filter { !it.falsePositive }) {
        val level = ((event.peakDb - QUIET_FLOOR_DB) / (LOUD_CEILING_DB - QUIET_FLOOR_DB)).coerceIn(0.15f, 1f)
        val barHeight = laneHeight * level
        val color = if (event.peakDb >= LOUD_DB) Palette.amber else Palette.primary.copy(alpha = 0.75f)
        // An episode is a time span: draw it at least 4dp wide so short ones stay tappable and visible.
        val left = x(event.startMs)
        val barWidth = (x(event.startMs + event.durationMs) - left).coerceAtLeast(4.dp.toPx())
        drawRoundRect(color, Offset(left, bottom - barHeight), Size(barWidth, barHeight), CornerRadius(2.dp.toPx()))
    }
}

private fun DrawScope.drawVital(
    points: List<VitalPoint>,
    min: Double,
    max: Double,
    x: (Long) -> Float,
    lane: Pair<Float, Float>,
    usable: Float,
    color: Color,
    strokeWidth: Float,
    dipBelow: Double? = null,
) {
    if (points.isEmpty()) return
    val top = usable * lane.first
    val bottom = usable * lane.second
    fun y(value: Double) = (bottom - ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() * (bottom - top))
    val path = Path()
    points.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(x(p.tsMs), y(p.value)) else path.lineTo(x(p.tsMs), y(p.value))
    }
    drawPath(path, color, style = Stroke(width = strokeWidth.dp.toPx() / 2f))
    if (dipBelow != null) {
        for (p in points.filter { it.value < dipBelow }) drawCircle(Palette.amber, 3.dp.toPx(), Offset(x(p.tsMs), y(p.value)))
    }
}
