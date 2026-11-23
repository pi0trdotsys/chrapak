package app.chrapak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.chrapak.detection.DetectionPrefs
import app.chrapak.detection.RoomCalibrator
import app.chrapak.detection.Sensitivity
import app.chrapak.detection.configFor
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How easily a sound counts as an episode, and a measurement of the bedroom's quiet level. Both
 * only change which sounds are saved; whether an episode counts as snoring is the model's call.
 * Changes apply from the next recording.
 */
@Composable
fun DetectionCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listening by ListeningState.status.collectAsState()
    var sensitivity by remember { mutableStateOf(DetectionPrefs.sensitivity(context)) }
    var floor by remember { mutableStateOf(DetectionPrefs.calibratedFloorDb(context)) }
    var measuring by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun measure() {
        measuring = true
        message = null
        scope.launch {
            val result = RoomCalibrator.calibrate(context)
            measuring = false
            if (result == null) {
                message = "Nie udało się odczytać mikrofonu."
            } else {
                DetectionPrefs.setCalibratedFloorDb(context, result)
                floor = result
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) measure() else message = "Pomiar wymaga dostępu do mikrofonu."
    }

    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Czułość wykrywania", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Jak cichy dźwięk zostaje zapisany. Wyższa łapie ciche chrapanie z daleka, ale zapisuje też więcej innych dźwięków. Czy to chrapanie, rozstrzyga potem model.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sensitivity.entries.forEach { s ->
                    FilterChip(
                        selected = sensitivity == s,
                        onClick = {
                            sensitivity = s
                            DetectionPrefs.setSensitivity(context, s)
                        },
                        label = { Text(s.label) },
                    )
                }
            }
            val threshold = configFor(sensitivity, floor).gate.absoluteMinDb
            Text(
                "Próg: ${threshold.roundToInt()} dBFS" + (floor?.let { " · cisza w pokoju: ${it.roundToInt()} dBFS" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Kalibracja pokoju", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Połóż telefon tam, gdzie leży w nocy, zrób ciszę i zmierz. W bardzo cichym pokoju próg się obniży, żeby nie przegapić cichego chrapania. Pomiar nigdy nie podnosi progu.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                measuring -> {
                    Text("Słucham ciszy przez ${RoomCalibrator.LISTEN_MS / 1000} s…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Palette.primary, trackColor = Palette.border)
                }
                listening.listening -> Text("Zatrzymaj nasłuch, żeby zmierzyć pokój.", color = Palette.amber)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) measure() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(if (floor == null) "Zmierz ciszę" else "Zmierz ponownie") }
                    if (floor != null) {
                        TextButton(onClick = {
                            DetectionPrefs.clearCalibratedFloor(context)
                            floor = null
                        }) { Text("Wyczyść") }
                    }
                }
            }
            message?.let { Text(it, color = Palette.amber, style = MaterialTheme.typography.bodySmall) }
            Text("Zmiany działają od następnego nagrania.", color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall)
        }
    }
}
