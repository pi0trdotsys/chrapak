package app.chrapak.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.data.NightEntity
import app.chrapak.data.NightRepository
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.NightHealth
import app.chrapak.report.NightReport
import app.chrapak.report.NightStats
import app.chrapak.ui.dateLabel
import app.chrapak.ui.dbLabel
import app.chrapak.ui.decimal
import app.chrapak.ui.durationLabel
import app.chrapak.ui.events.ClipPlayer
import app.chrapak.ui.events.EventSheet
import app.chrapak.ui.stageLabel
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.LocalPalette
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class NightData(val events: List<SnoreEventEntity>, val health: NightHealth, val connected: Boolean)

@Composable
fun ReportScreen(
    repo: NightRepository,
    nightId: String?,
    version: Int,
    player: ClipPlayer,
    onNightChange: (String) -> Unit,
    onOpenEvents: (events: List<SnoreEventEntity>, health: NightHealth) -> Unit,
    onChanged: () -> Unit,
    onAnalyze: (SnoreEventEntity) -> Unit,
) {
    val nights by produceState<List<NightEntity>?>(null, version) { value = repo.nights() }
    val all = nights
    if (all == null) {
        Text("Wczytywanie…", Modifier.padding(24.dp), color = Palette.mutedForeground)
        return
    }
    if (all.isEmpty()) {
        EmptyReport()
        return
    }
    val index = (all.indexOfFirst { it.id == nightId }.takeIf { it >= 0 }) ?: 0
    val night = all[index]
    val data by produceState<NightData?>(null, night.id, version) {
        value = null
        val events = repo.events(night.id)
        value = NightData(events, repo.health(night, events), repo.healthConnected())
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("TWÓJ PORANNY RAPORT", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
            Text("Dzień dobry, spokojnie.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        val listening by ListeningState.status.collectAsState()
        // Only the night the service is recording right now is "in progress"; an unclosed older one was cut short.
        val inProgress = listening.listening && night.sleepEndMs == null && night.sleepStartMs >= (listening.sinceMs ?: Long.MAX_VALUE) - 10_000
        NightSelector(
            night = night,
            inProgress = inProgress,
            canPrev = index < all.lastIndex,
            canNext = index > 0,
            onPrev = { onNightChange(all[index + 1].id) },
            onNext = { onNightChange(all[index - 1].id) },
        )
        val current = data
        if (current == null) {
            Text("Wczytywanie nocy…", color = Palette.mutedForeground)
        } else {
            NightContent(repo, night, current, player, onOpenEvents, onChanged, onAnalyze)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EmptyReport() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ta noc jest jeszcze przed Tobą", style = MaterialTheme.typography.headlineSmall)
        Text("Nie ma jeszcze nagrań. Włącz nasłuch na ekranie Noc przed snem.", color = Palette.mutedForeground)
    }
}

@Composable
private fun NightSelector(night: NightEntity, inProgress: Boolean, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrev, enabled = canPrev) { Text("‹") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateLabel(night.sleepStartMs), style = MaterialTheme.typography.titleMedium)
                val end = night.sleepEndMs
                Text(
                    if (end == null) "od ${timeLabel(night.sleepStartMs)} · ${if (inProgress) "w trakcie" else "przerwana"}" else "${timeLabel(night.sleepStartMs)} – ${timeLabel(end)} · ${durationLabel(end - night.sleepStartMs)}",
                    color = Palette.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onNext, enabled = canNext) { Text("›") }
        }
    }
}

@Composable
private fun NightContent(
    repo: NightRepository,
    night: NightEntity,
    data: NightData,
    player: ClipPlayer,
    onOpenEvents: (List<SnoreEventEntity>, NightHealth) -> Unit,
    onChanged: () -> Unit,
    onAnalyze: (SnoreEventEntity) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stats = NightReport.stats(night, data.events)
    var selected by remember(night.id, data.events) { mutableStateOf<SnoreEventEntity?>(null) }
    val endMs = repo.endOf(night, data.events)

    if (data.events.isEmpty()) {
        Text("W tej nocy nie wykryto chrapania.", color = Palette.mutedForeground)
    }
    ScoreSection(stats, stillRecording = night.sleepEndMs == null)
    StatsGrid(stats)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Twoja noc, w szczegółach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
            Column(Modifier.padding(12.dp)) {
                Timeline(night.sleepStartMs, endMs, data.events, data.health, onEventTap = { selected = it }, connected = data.connected)
                Legend()
            }
        }
        SourceNote(data.health)
        OutlinedButton(onClick = { onOpenEvents(data.events, data.health) }, modifier = Modifier.fillMaxWidth(), enabled = data.events.isNotEmpty()) {
            Text("Zobacz wszystkie nagrania (${stats.count})")
        }
    }

    Insights(stats, data)

    selected?.let { event ->
        EventSheet(
            event = event,
            health = data.health,
            file = repo.clipFile(event),
            player = player,
            onStar = { scope.launch { repo.setStarred(event, !event.starred); selected = null; onChanged() } },
            onFalsePositive = { scope.launch { repo.setFalsePositive(event, !event.falsePositive); selected = null; onChanged() } },
            onDelete = { scope.launch { player.stop(); repo.delete(event); selected = null; onChanged() } },
            onAnalyze = {
                selected = null
                onAnalyze(event)
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun ScoreSection(stats: NightStats, stillRecording: Boolean) {
    val colors = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(colors.border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(colors.primary, -90f, 360f * stats.score / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stats.tooShort) "–" else "${stats.score}", style = MaterialTheme.typography.headlineLarge)
                Text("na 100", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stats.tooShort) {
                Text(if (stillRecording) "Nagrywam" else "Krótkie nagranie", color = Palette.primary, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (stillRecording) "Wynik pojawi się po 30 minutach nagrania." else "Wynik liczę dla nagrań od 30 minut. Chrapanie zajęło ${stats.percent}% czasu.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text("${NightReport.scoreLabel(stats.score)} chrapanie", color = Palette.primary, style = MaterialTheme.typography.labelLarge)
                Text("Chrapanie zajęło ${stats.percent}% nocy.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: NightStats) {
    val cells = listOf(
        "Czas chrapania" to durationLabel(stats.snoringMs),
        "Część nocy" to "${stats.percent}%",
        "Liczba epizodów" to "${stats.count}",
        "Najgłośniej" to (stats.peakDb?.let { dbLabel(it) } ?: "–"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Palette.card)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.mutedForeground)
                            Text(value, style = MaterialTheme.typography.titleLarge, color = if (label == "Najgłośniej") Palette.amber else Palette.foreground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Sen" to Palette.primary, "Chrapanie" to Palette.amber, "SpO₂" to Palette.teal, "Tętno" to Palette.heart).forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(color) }
                Spacer(Modifier.width(5.dp))
                Text(name, style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
            }
        }
    }
}

@Composable
private fun SourceNote(health: NightHealth) {
    val origins = (health.spo2 + health.heartRate).map { it.origin }.distinct()
    val text = when {
        health.isEmpty -> "Brak danych z Health Connect dla tej nocy."
        else -> "Dane z Health Connect: " + origins.joinToString(", ").ifEmpty { "sen" }
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
}

@Composable
private fun Insights(stats: NightStats, data: NightData) {
    val cards = buildList {
        NightReport.remRatio(data.events, data.health.stages)?.takeIf { it >= 1.3 }?.let {
            add("Chrapanie lubi fazę REM" to "W fazie REM chrapiesz ${decimal(it)}× częściej niż w pozostałych fazach snu.")
        }
        NightReport.lowestSpo2NearEvents(data.events, data.health.spo2)?.let { dip ->
            val value = dip.value.roundToInt()
            if (dip.value < 92.0) {
                add("Warto obserwować SpO₂" to "W czasie chrapania lub do 90 s po nim saturacja spadła do $value% o ${timeLabel(dip.tsMs)}.")
            } else {
                add("SpO₂ w normie" to "Najniższa saturacja w czasie chrapania wyniosła $value%.")
            }
        }
        stats.longest?.let { e ->
            val stage = NightReport.stageAt(data.health.stages, e.startMs)?.let { " · ${stageLabel(it)}" }.orEmpty()
            add("Najdłuższy epizod" to "${durationLabel(e.durationMs)} nieprzerwanego chrapania o ${timeLabel(e.startMs)}$stage.")
        }
    }
    if (cards.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wnioski z tej nocy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        cards.forEach { (title, text) ->
            Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text, color = Palette.mutedForeground, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Obserwacje, nie diagnoza. Jeśli coś Cię niepokoi, porozmawiaj z lekarzem.", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
    }
}
