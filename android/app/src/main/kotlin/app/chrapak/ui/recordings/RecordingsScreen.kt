package app.chrapak.ui.recordings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.data.NightRecordings
import app.chrapak.data.NightRepository
import app.chrapak.data.isNap
import app.chrapak.ui.dateLabel
import app.chrapak.ui.durationLabel
import app.chrapak.ui.plural
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import java.util.Locale

/** Every night that has recordings, newest first. A tap opens that night's clips. */
@Composable
fun RecordingsScreen(repo: NightRepository, version: Int, onOpenNight: (NightRecordings) -> Unit) {
    val recordings by produceState<List<NightRecordings>?>(null, version) { value = repo.recordings() }
    val list = recordings?.filter { it.events.isNotEmpty() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("ARCHIWUM", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
        Text("Nagrania", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        when {
            list == null -> Text("Wczytuję…", color = Palette.mutedForeground, modifier = Modifier.padding(top = 12.dp))
            list.isEmpty() -> Text(
                "Jeszcze nie ma nagrań. Pojawią się tu po pierwszej nocy lub drzemce z nasłuchem.",
                color = Palette.mutedForeground,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> {
                Text(
                    "${list.sumOf { it.events.size }.let { "$it ${plural(it, "nagranie", "nagrania", "nagrań")}" }} · ${sizeLabel(list.sumOf { it.clipBytes })} na telefonie",
                    color = Palette.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.night.id }) { NightRow(it, onOpenNight) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NightRow(item: NightRecordings, onOpen: (NightRecordings) -> Unit) {
    val night = item.night
    Card(
        colors = CardDefaults.cardColors(containerColor = Palette.card),
        modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val kind = if (night.isNap()) "Drzemka" else "Noc"
            Text("$kind · ${dateLabel(night.sleepStartMs)}", style = MaterialTheme.typography.titleMedium)
            val end = night.sleepEndMs
            val span = if (end != null) "${timeLabel(night.sleepStartMs)}–${timeLabel(end)} (${durationLabel(end - night.sleepStartMs)})"
            else "od ${timeLabel(night.sleepStartMs)}, trwa"
            Text(span, color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall)
            val parts = buildList {
                add("${item.snoring} ${plural(item.snoring, "epizod", "epizody", "epizodów")} chrapania")
                if (item.uncertain > 0) add("${item.uncertain} ${plural(item.uncertain, "niepewny", "niepewne", "niepewnych")}")
                add(sizeLabel(item.clipBytes))
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.forLanguageTag("pl"), "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> "${bytes / 1024} kB"
    else -> "$bytes B"
}
