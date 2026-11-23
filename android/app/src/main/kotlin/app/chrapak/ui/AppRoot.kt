package app.chrapak.ui

import androidx.activity.compose.BackHandler
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
import app.chrapak.data.RecordingsViewModel
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

private enum class Tab(val label: String) { NOC("Noc"), RAPORT("Raport"), NAZRANIA("Nagrania"), USTAWIENIA("Ustawienia") }

private class EventsView(val events: List<SnoreEventEntity>, val health: NightHealth)

@Composable
fun AppRoot(repo: NightRepository, health: HealthConnectSource, openReportRequest: Int = 0, exporter: androidx.compose.runtime.ProvedStateValue<androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri>>?) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOC) }
    var nightId by rememberSaveable { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var eventsView by remember { mutableStateOf<EventsView?>(null) }
    var analysing by remember { mutableStateOf<SnoreEventEntity?>(null) }
    val player = remember { ClipPlayer() }
    // A tap on the morning summary asks for the report, even if the app was already open on another tab.
    LaunchedEffect(openReportRequest) {
        if (openReportRequest > 0) {
            eventsView = null
            nightId = null
            tab = Tab.RAPORT
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
                clip != null && tab == Tab.RAPORT -> ClipAnalysisScreen(clip, repo.clipFile(clip), onBack = { analysing = null })
                tab == Tab.NOC -> TonightScreen(onOpenSettings = { tab = Tab.USTAWIENIA })
                tab == Tab.NAZRANIA -> {
                    val vm = remember { RecordingsViewModel(repo.context, repo.db, health) }
                    val recordings by remember { vm.all() }
                    RecordingsScreen(
                        recordings = recordings,
                        repo = repo,
                        onBack = { tab = Tab.RAPORT },
                        onEventClick = { e ->
                            player.stop()
                            eventsView = EventsView(listOf(e), NightHealth(emptyList(), emptyList(), emptyList()))
                            tab = Tab.RAPORT
                        },
                        onExport = { e ->
                            val clip = repo.clipFile(e)
                            clip?.let { c ->
                                val intent = app.chrapak.export.ClipExporter(repo.context).export(e, c)
                                exporter?.launch(intent)
                            }
                        },
                    )
                }
                tab == Tab.USTAWIENIA -> SettingsScreen(health, repo)
                events != null -> EventsScreen(
                    repo = repo,
                    events = events.events,
                    health = events.health,
                    player = player,
                    onBack = { eventsView = null },
                    onChanged = {
                        version++
                        eventsView = null
                    },
                    onAnalyze = { analysing = it },
                    onExport = { e ->
                        val clip = repo.clipFile(e)
                        clip?.let { c ->
                            val intent = app.chrapak.export.ClipExporter(repo.context).export(e, c)
                            exporter?.launch(intent)
                        }
                    },
                )
                else -> ReportScreen(
                    repo = repo,
                    nightId = nightId,
                    version = version,
                    player = player,
                    onNightChange = { nightId = it },
                    onOpenEvents = { e, h -> eventsView = EventsView(e, h) },
                    onChanged = { version++ },
                    onAnalyze = { analysing = it },
                )
            }
        }
    }
}
