package app.chrapak.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler
import app.chrapak.schedule.NotificationPrefs
import app.chrapak.schedule.WeeklyDigestNotifier
import app.chrapak.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Which notifications the app may send. The night's summary is the only one that arrives without
 * asking; the weekly digest and the evening reminder are opt-out and can be tried on the spot.
 */
@Composable
fun NotificationsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summaryOn by remember { mutableStateOf(AutoListenPrefs.summaryOn(context)) }
    var digestOn by remember { mutableStateOf(!NotificationPrefs.digestOff(context)) }
    var prepOn by remember { mutableStateOf(!NotificationPrefs.prepOff(context)) }
    var sending by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Powiadomienia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Wszystkie są lokalne: nic nie wychodzi z telefonu. Możesz je wyłączyć tutaj albo w systemowych ustawieniach Androida.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )

            Toggle(
                label = "Podsumowanie po nocy",
                checked = summaryOn,
                onChange = {
                    summaryOn = it
                    AutoListenPrefs.setSummaryOn(context, it)
                },
            )
            Text(
                "Po zakończeniu nasłuchu: ile chrapania, jaki wynik, a gdy model nie był pewny — ile nagrań warto przesłuchać.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )

            Toggle(
                label = "Tygodniowe podsumowanie (niedziela 10:00)",
                checked = digestOn,
                onChange = {
                    digestOn = it
                    NotificationPrefs.setDigestOff(context, !it)
                    AutoListenScheduler.reschedule(context)
                },
            )
            Text(
                "Liczba nocy, średni wynik i ile nocy było gorszych od poprzedniej. Działa też bez włączonego czuwania.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )

            Toggle(
                label = "Przypomnienie o ładowarce (22:30)",
                checked = prepOn,
                onChange = {
                    prepOn = it
                    NotificationPrefs.setPrepOff(context, !it)
                    AutoListenScheduler.reschedule(context)
                },
            )
            Text(
                "Wieczorem, gdy czuwanie jest włączone, a telefon nie jest ładowany. Akcje: „Włącz teraz” i „Nie dziś”.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                onClick = {
                    sending = true
                    scope.launch {
                        val shown = runCatching { WeeklyDigestNotifier.post(context) }.getOrDefault(false)
                        sending = false
                        Toast.makeText(
                            context,
                            if (shown) "Wysłano podsumowanie" else "Brak nocy z ostatniego tygodnia",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
            ) { Text(if (sending) "Wysyłam…" else "Wyślij podsumowanie testowe") }
        }
    }
}

/** One switch with a label, used for every notification toggle so they line up. */
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}