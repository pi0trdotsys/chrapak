package app.chrapak.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chrapak.data.NightRepository
import app.chrapak.data.SnoreEventEntity
import app.chrapak.data.isSnoring
import app.chrapak.data.isUncertain
import app.chrapak.health.NightHealth
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import kotlinx.coroutines.launch

private enum class Filter(val label: String) {
    SNORING("Chrapanie"),
    UNCERTAIN("Niepewne"),
    STARRED("Zachowane"),
    FALSE("Fałszywe"),
}

private fun Filter.accepts(e: SnoreEventEntity) = when (this) {
    Filter.SNORING -> e.isSnoring
    Filter.UNCERTAIN -> e.isUncertain
    Filter.STARRED -> e.starred
    Filter.FALSE -> e.falsePositive
}

@Composable
fun EventsScreen(
    repo: NightRepository,
    events: List<SnoreEventEntity>,
    health: NightHealth,
    player: ClipPlayer,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onAnalyze: (SnoreEventEntity) -> Unit,
    onSave: (SnoreEventEntity) -> Unit,
    onShare: (SnoreEventEntity) -> Unit,
    backLabel: String = "‹ Raport",
    title: String = "Nagrania chrapania",
) {
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(Filter.SNORING) }
    var pendingDelete by remember { mutableStateOf<SnoreEventEntity?>(null) }
    val shown = events.filter { filter.accepts(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = {
                player.stop()
                onBack()
            }) { Text(backLabel) }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                val n = events.count { f.accepts(it) }
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} $n") })
            }
        }
        if (filter == Filter.UNCERTAIN && shown.isNotEmpty()) {
            Text(
                "Dźwięki, które przeszły przez bramkę, ale model nie był pewny, że to chrapanie. Nie liczą się do wyniku. Po 7 dniach znikają, chyba że je zachowasz.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (shown.isEmpty()) {
            Text("Brak nagrań w tym widoku.", color = Palette.mutedForeground)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { event ->
                val file = repo.clipFile(event)
                Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(timeLabel(event.startMs) + if (event.starred) "  ★" else "", style = MaterialTheme.typography.titleMedium)
                        Text(eventFacts(event, health).joinToString(" · "), color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall)
                        // Two rows: five actions in one line do not fit a phone and wrap letter by letter.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PlayButton(event, file, player)
                            OutlinedButton(onClick = {
                                player.stop()
                                onAnalyze(event)
                            }, enabled = file != null) { Text("Analiza") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(onClick = { scope.launch { repo.setStarred(event, !event.starred); onChanged() } }) {
                                Text(if (event.starred) "Odepnij" else "Zachowaj")
                            }
                            TextButton(onClick = { scope.launch { repo.setFalsePositive(event, !event.falsePositive); onChanged() } }) {
                                Text(if (event.falsePositive) "Przywróć" else "Fałszywy")
                            }
                            MoreMenu(
                                hasFile = file != null,
                                onSave = { onSave(event) },
                                onShare = { onShare(event) },
                                onDelete = { pendingDelete = event },
                            )
                        }
                        if (player.playingId == event.id) {
                            LinearProgressIndicator(progress = { player.progress }, modifier = Modifier.fillMaxWidth(), color = Palette.primary, trackColor = Palette.border)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Usunąć nagranie?") },
            text = { Text("Epizod o ${timeLabel(event.startMs)} i jego plik audio zostaną usunięte. Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    if (player.playingId == event.id) player.stop()
                    scope.launch { repo.delete(event); onChanged() }
                }) { Text("Usuń", color = Palette.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Anuluj") } },
        )
    }
}

/** Less frequent actions behind one button, so a row of actions fits a phone screen. */
@Composable
private fun MoreMenu(hasFile: Boolean, onSave: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Więcej") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Udostępnij") }, enabled = hasFile, onClick = { open = false; onShare() })
            DropdownMenuItem(text = { Text("Zapisz w plikach") }, enabled = hasFile, onClick = { open = false; onSave() })
            DropdownMenuItem(text = { Text("Usuń", color = Palette.destructive) }, onClick = { open = false; onDelete() })
        }
    }
}
