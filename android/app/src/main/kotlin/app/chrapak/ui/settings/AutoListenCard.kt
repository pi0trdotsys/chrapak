package app.chrapak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.AutoListenScheduler
import app.chrapak.schedule.ScheduleMath
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.Palette

/** Standby on/off, the daily window, and how naps work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoListenCard() {
    val context = LocalContext.current
    val status by ListeningState.status.collectAsState()
    var startMin by remember { mutableIntStateOf(AutoListenPrefs.startMin(context)) }
    var endMin by remember { mutableIntStateOf(AutoListenPrefs.endMin(context)) }
    var scheduleOn by remember { mutableStateOf(AutoListenPrefs.scheduleOn(context)) }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) ListeningService.arm(context)
    }

    fun applyChange() {
        AutoListenScheduler.reschedule(context)
        // Arm again so a window that is already open starts at once and the notification text is refreshed.
        if (status.armed) ListeningService.arm(context)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Automatyczny nasłuch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(
                    checked = status.armed,
                    onCheckedChange = { on ->
                        when {
                            !on -> ListeningService.disarm(context)
                            permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } -> ListeningService.arm(context)
                            else -> launcher.launch(permissions)
                        }
                    },
                )
            }
            Text(
                if (status.armed) "Czuwam. Telefon nic nie nagrywa, dopóki nie zacznie się okno poniżej albo nie włączysz nasłuchu sam."
                else "Włącz czuwanie raz, a nasłuch będzie startował sam. W powiadomieniu zobaczysz, że czuwam.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Włączaj o stałej porze", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = scheduleOn,
                    onCheckedChange = {
                        scheduleOn = it
                        AutoListenPrefs.setScheduleOn(context, it)
                        applyChange()
                    },
                )
            }
            if (scheduleOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editingStart = true }, modifier = Modifier.weight(1f)) { Text("Od ${ScheduleMath.format(startMin)}") }
                    OutlinedButton(onClick = { editingStart = false }, modifier = Modifier.weight(1f)) { Text("Do ${ScheduleMath.format(endMin)}") }
                }
            }
            Text(
                "Drzemki: dodaj kafelek „Chrapak” do Szybkich ustawień. Jedno dotknięcie włącza nasłuch na 3 godziny albo do ponownego dotknięcia.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Pierścień nie uruchomi nasłuchu w chwili zaśnięcia: RingConn zapisuje sen do Health Connect z opóźnieniem wielu godzin, więc dane wiedzą o śnie dopiero po fakcie.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    editingStart?.let { isStart ->
        val initial = if (isStart) startMin else endMin
        val picker = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingStart = null },
            title = { Text(if (isStart) "Początek nasłuchu" else "Koniec nasłuchu") },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = picker.hour * 60 + picker.minute
                    if (isStart) startMin = minutes else endMin = minutes
                    AutoListenPrefs.setWindow(context, startMin, endMin)
                    editingStart = null
                    applyChange()
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { editingStart = null }) { Text("Anuluj") } },
        )
    }
}
