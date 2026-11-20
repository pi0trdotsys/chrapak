package app.chrapak.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.NightHealth
import app.chrapak.report.NightReport
import app.chrapak.ui.dbLabel
import app.chrapak.ui.durationLabel
import app.chrapak.ui.stageLabel
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/** Context for one episode: when, how loud, what sleep stage and SpO2 around it. */
fun eventFacts(event: SnoreEventEntity, health: NightHealth): List<String> {
    val facts = mutableListOf("${durationLabel(event.durationMs)} · szczyt ${dbLabel(event.peakDb)}")
    NightReport.stageAt(health.stages, event.startMs)?.let { facts += stageLabel(it) }
    val dip = NightReport.lowestSpo2NearEvents(listOf(event), health.spo2)
    if (dip != null) facts += "SpO₂ min ${dip.value.roundToInt()}%"
    facts += "pewność ${(event.confidence * 100).roundToInt()}%"
    return facts
}

@Composable
fun PlayButton(event: SnoreEventEntity, file: File?, player: ClipPlayer) {
    val playing = player.playingId == event.id
    OutlinedButton(onClick = { if (file != null) player.toggle(event.id, file) }, enabled = file != null) {
        Text(if (playing) "Wstrzymaj" else "Odtwórz")
    }
    LaunchedEffect(playing) {
        while (playing) {
            player.tick()
            delay(200)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    event: SnoreEventEntity,
    health: NightHealth,
    file: File?,
    player: ClipPlayer,
    onStar: () -> Unit,
    onFalsePositive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = {
            player.stop()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NAGRANIE CHRAPANIA", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
            Text("Epizod o ${timeLabel(event.startMs)}", style = MaterialTheme.typography.headlineSmall)
            eventFacts(event, health).forEach { Text(it, color = Palette.mutedForeground) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayButton(event, file, player)
            }
            if (player.playingId == event.id) {
                LinearProgressIndicator(progress = { player.progress }, modifier = Modifier.fillMaxWidth(), color = Palette.primary, trackColor = Palette.border)
            }
            if (file == null) Text("Brak pliku audio dla tego epizodu.", color = Palette.mutedForeground)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onStar) { Text(if (event.starred) "Odepnij" else "Zachowaj") }
                TextButton(onClick = onFalsePositive) { Text(if (event.falsePositive) "To chrapanie" else "Fałszywy alarm") }
                TextButton(onClick = onDelete) { Text("Usuń", color = Palette.destructive) }
            }
        }
    }
}
