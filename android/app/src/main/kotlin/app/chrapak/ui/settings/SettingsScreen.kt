package app.chrapak.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import app.chrapak.health.HealthConnectSource
import app.chrapak.health.HealthStatus
import app.chrapak.health.OriginSummary
import app.chrapak.ui.theme.Palette

private const val DAY_MS = 24 * 60 * 60_000L

/** Settings for now: connecting Health Connect and showing which apps actually write data there. */
@Composable
fun SettingsScreen(health: HealthConnectSource) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh++ }
    val status = health.status()
    val granted by produceState(false, refresh) { value = health.hasPermissions() }
    val summary by produceState<List<OriginSummary>?>(null, refresh, granted) {
        val now = System.currentTimeMillis()
        value = if (granted) runCatching { health.summarize(now - 3 * DAY_MS, now) }.getOrDefault(emptyList()) else null
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("PO TWOJEMU", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
        Text("Ustawienia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Źródła danych", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Chrapak czyta fazy snu, saturację i tętno z Health Connect, gdzie zapisują je aplikacje pierścienia i opaski. Tylko odczyt, dostęp możesz cofnąć w ustawieniach Health Connect.",
                    color = Palette.mutedForeground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    status == HealthStatus.UNAVAILABLE -> Text("Health Connect nie jest dostępny na tym telefonie.", color = Palette.destructive)
                    status == HealthStatus.UPDATE_REQUIRED -> Text("Zaktualizuj Health Connect w Sklepie Play.", color = Palette.destructive)
                    !granted -> Button(onClick = { launcher.launch(health.permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Połącz Health Connect") }
                    else -> Text("Połączono. Dostęp: sen, tętno, saturacja.", color = Palette.teal)
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = status == HealthStatus.AVAILABLE,
                ) { Text("Otwórz Health Connect") }
            }
        }

        if (granted) {
            Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Co jest w Health Connect (ostatnie 3 dni)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val list = summary
                    when {
                        list == null -> Text("Wczytywanie…", color = Palette.mutedForeground)
                        list.isEmpty() -> Text(
                            "Brak danych o śnie, tętnie i saturacji. Sprawdź w aplikacji RingConn lub Mi Fitness, czy synchronizacja z Health Connect jest włączona.",
                            color = Palette.mutedForeground,
                        )
                        else -> list.forEach { o ->
                            Column {
                                Text(o.label, fontWeight = FontWeight.Bold)
                                Text(
                                    "sesje snu: ${o.sleepSessions} (fazy: ${o.stageSegments}) · tętno: ${o.heartRateSamples} · SpO₂: ${o.spo2Samples}",
                                    color = Palette.mutedForeground,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Odśwież") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
