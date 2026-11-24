package app.chrapak.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.chrapak.export.ClipExporter
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chrapak.data.NightRepository
import app.chrapak.data.SnoreEventEntity
import app.chrapak.health.HealthConnectSource
import app.chrapak.health.NightHealth
import app.chrapak.ui.events.ClipPlayer
import app.chrapak.ui.events.ClipAnalysisScreen
import app.chrapak.ui.events.EventsScreen
import app.chrapak.ui.recordings.RecordingsScreen
import app.chrapak.ui.report.ReportScreen
import app.chrapak.ui.settings.SettingsScreen
import app.chrapak.ui.theme.Palette

private enum class Tab(val label: String) { NOC("Noc"), RAPORT("Raport"), NAGRANIA("Nagrania"), USTAWIENIA("Ustawienia") }

/** A night's clip list, opened from the report or from the Recordings tab. */
private data class EventsView(
    val nightId: String,
    val events: List<SnoreEventEntity>,
    val health: NightHealth,
    val backLabel: String,
)

@Composable
fun AppRoot(repo: NightRepository, health: HealthConnectSource, openReportRequest: Int = 0, openRecordingsRequest: Int = 0) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOC) }
    var nightId by rememberSaveable { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var eventsView by remember { mutableStateOf<EventsView?>(null) }
    var analysing by remember { mutableStateOf<SnoreEventEntity?>(null) }
    val player = remember { ClipPlayer() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<SnoreEventEntity?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val event = saving
        saving = null
        val clip = event?.let { repo.clipFile(it) }
        if (uri != null && clip != null) {
            scope.launch {
                val ok = ClipExporter.copyTo(context, clip, uri)
                Toast.makeText(context, if (ok) "Zapisano nagranie" else "Nie udało się zapisać nagrania", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onSave: (SnoreEventEntity) -> Unit = { event ->
        saving = event
        saveLauncher.launch(ClipExporter.fileName(event))
    }
    val onShare: (SnoreEventEntity) -> Unit = { event ->
        repo.clipFile(event)?.let { context.startActivity(ClipExporter.shareIntent(context, event, it)) }
    }
    // A tap on the morning summary asks for the report, even if the app was already open on another tab.
    LaunchedEffect(openReportRequest) {
        if (openReportRequest > 0) {
            eventsView = null
            nightId = null
            tab = Tab.RAPORT
        }
    }
    // A tap on the "Posłuchaj" action asks for the recordings tab.
    LaunchedEffect(openRecordingsRequest) {
        if (openRecordingsRequest > 0) {
            eventsView = null
            analysing = null
            tab = Tab.NAGRANIA
        }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    BackHandler(enabled = analysing != null) { analysing = null }
    BackHandler(enabled = eventsView != null && analysing == null) {
        player.stop()
        eventsView = null
    }

    Scaffold(
        containerColor = Palette.background,
        bottomBar = {
            NavigationBar(containerColor = Palette.card) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            player.stop()
                            eventsView = null
                            analysing = null
                            tab = t
                        },
                        icon = {
                            val color = if (tab == t) Palette.primary else Palette.mutedForeground
                            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Palette.primary,
                            unselectedTextColor = Palette.mutedForeground,
                            indicatorColor = Palette.softPrimary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val events = eventsView
            val clip = analysing
            when {
                clip != null -> ClipAnalysisScreen(clip, repo.clipFile(clip), onBack = { analysing = null })
                tab == Tab.NOC -> TonightScreen(onOpenSettings = { tab = Tab.USTAWIENIA })
                tab == Tab.USTAWIENIA -> SettingsScreen(health, repo)
                events != null -> EventsScreen(
                    repo = repo,
                    events = events.events,
                    health = events.health,
                    player = player,
                    onBack = { eventsView = null },
                    onChanged = {
                        version++
                        scope.launch { eventsView = events.copy(events = repo.events(events.nightId)) }
                    },
                    onAnalyze = { analysing = it },
                    onSave = onSave,
                    onShare = onShare,
                    backLabel = events.backLabel,
                )
                tab == Tab.NAGRANIA -> RecordingsScreen(repo, version, onOpenNight = { item ->
                    scope.launch {
                        val h = runCatching { repo.health(item.night, item.events) }.getOrElse { NightHealth(emptyList(), emptyList(), emptyList()) }
                        eventsView = EventsView(item.night.id, item.events, h, "‹ Nagrania")
                    }
                })
                else -> ReportScreen(
                    repo = repo,
                    nightId = nightId,
                    version = version,
                    player = player,
                    onNightChange = { nightId = it },
                    onOpenEvents = { e, h -> eventsView = EventsView(e.firstOrNull()?.nightId ?: nightId.orEmpty(), e, h, "‹ Raport") },
                    onChanged = { version++ },
                    onAnalyze = { analysing = it },
                )
            }
        }
    }
}
