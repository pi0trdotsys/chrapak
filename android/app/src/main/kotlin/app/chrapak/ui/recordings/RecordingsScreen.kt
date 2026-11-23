package app.chrapak.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chrapak.data.NightRecordings
import app.chrapak.data.NightRepository
import app.chrapak.data.SnoreEventEntity
import app.chrapak.export.ClipExporter
import app.chrapak.ui.dbLabel
import app.chrapak.ui.durationLabel
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import java.io.File
import kotlin.math.roundToInt

private enum class Filter(val label: String) {
    ALL("Wszystkie"),
    CONFIDENT("Pewne"),
    STARRED("Zachowane"),
    FALSE("Fałszywe"),
}

private fun Filter.accepts(e: SnoreEventEntity) = when (this) {
    Filter.ALL -> true
    Filter.CONFIDENT -> e.confidence >= 0.3f && !e.falsePositive
    Filter.STARRED -> e.starred
    Filter.FALSE -> e.falsePositive
}

@Composable
fun RecordingsScreen(
    recordings: List<NightRecordings>,
    repo: NightRepository,
    onBack: () -> Unit,
    onEventClick: (SnoreEventEntity) -> Unit,
    onExport: (SnoreEventEntity) -> Unit,
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    var selectedNightId by remember { mutableStateOf<String?>(null) }

    val filteredEvents = if (selectedNightId == null) {
        recordings.flatMap { it.events }.filter { filter.accepts(it) }
    } else {
        recordings.find { it.night.id == selectedNightId }?.events?.filter { filter.accepts(it) } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Raport") }
        }

        Text("Nagrania chrapania", style = MaterialTheme.typography.headlineSmall)

        val totalSize = recordings.sumOf { it.totalSizeBytes }
        Text(
            "${recordings.sumOf { it.events.size }} nagrań · ${formatSize(totalSize)}",
            color = Palette.mutedForeground,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.entries.forEach { f ->
                OutlinedButton(
                    selected = filter == f,
                    onClick = { filter = f },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(f.label)
                }
            }
        }

        if (filteredEvents.isEmpty()) {
            Text("Brak nagrań w tym widoku.", color = Palette.mutedForeground)
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    val file = repo.clipFile(event)
                    EventCard(event, file, onEventClick, onExport)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: SnoreEventEntity, file: File?, onClick: (SnoreEventEntity) -> Unit, onExport: (SnoreEventEntity) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(timeLabel(event.startMs) + if (event.starred) "  ★" else "", style = MaterialTheme.typography.titleMedium)
            Text(eventFacts(event).joinToString(" · "), color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onClick(event) }, enabled = file != null, modifier = Modifier.fillMaxWidth()) {
                Text("Odtwórz" + if (file == null) " (brak pliku)" else "")
            }
            OutlinedButton(onClick = { onExport(event) }, enabled = file != null, modifier = Modifier.fillMaxWidth()) {
                Text("Eksportuj")
            }
        }
    }
}

@Composable
private fun eventFacts(event: SnoreEventEntity): List<String> {
    val facts = mutableListOf("${durationLabel(event.durationMs)} · szczyt ${dbLabel(event.peakDb)}")
    facts += "pewność ${(event.confidence * 100).roundToInt()}%"
    return facts
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024.0)
    return if (mb >= 1) "${"%.1f".format(mb)} MB" else "${bytes} B"
}