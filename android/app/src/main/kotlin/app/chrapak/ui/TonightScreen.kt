package app.chrapak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.Palette
import kotlin.math.roundToInt

/** "Noc": start and stop listening. */
@Composable
fun TonightScreen() {
    val status by ListeningState.status.collectAsState()
    val context = LocalContext.current
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Notifications are optional; the microphone is not.
        if (result[Manifest.permission.RECORD_AUDIO] == true) ListeningService.start(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (status.listening) "Nasłuch aktywny" else "Spokojnej nocy",
            style = MaterialTheme.typography.headlineMedium,
        )
        if (status.listening) {
            Text("Wykryto zdarzeń: ${status.eventCount}")
            Text(
                if (status.classifierActive) "Klasyfikator: aktywny" else "Klasyfikator: brak modelu (sama bramka)",
                color = Palette.mutedForeground,
            )
            status.levelDb?.let { Text("Poziom: ${it.roundToInt()} dBFS", color = Palette.mutedForeground) }
        }
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (status.listening) {
                    ListeningService.stop(context)
                } else if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                    ListeningService.start(context)
                } else {
                    launcher.launch(permissions)
                }
            },
        ) {
            Text(if (status.listening) "Zatrzymaj" else "Rozpocznij nasłuch")
        }
        Text(
            "Dźwięk jest analizowany na telefonie. Nic nie wychodzi poza urządzenie.",
            color = Palette.mutedForeground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
