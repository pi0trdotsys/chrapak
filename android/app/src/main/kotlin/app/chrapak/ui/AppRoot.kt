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
import app.chrapak.ui.events.EventsScreen
import app.chrapak.ui.report.ReportScreen
import app.chrapak.ui.settings.SettingsScreen
import app.chrapak.ui.theme.Palette

private enum class Tab(val label: String) { NOC("Noc"), RAPORT("Raport"), USTAWIENIA("Ustawienia") }

private class EventsView(val events: List<SnoreEventEntity>, val health: NightHealth)

@Composable
fun AppRoot(repo: NightRepository, health: HealthConnectSource) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOC) }
    var nightId by rememberSaveable { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var eventsView by remember { mutableStateOf<EventsView?>(null) }
    val player = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    BackHandler(enabled = eventsView != null) {
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
            when {
                tab == Tab.NOC -> TonightScreen()
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
                )
                else -> ReportScreen(
                    repo = repo,
                    nightId = nightId,
                    version = version,
                    player = player,
                    onNightChange = { nightId = it },
                    onOpenEvents = { e, h -> eventsView = EventsView(e, h) },
                    onChanged = { version++ },
                )
            }
        }
    }
}
