package app.chrapak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import kotlin.math.roundToInt

// "Północ" tokens from the web prototype (src/styles.css).
private val PolnocColors = darkColorScheme(
    background = Color(0xFF080B14),
    surface = Color(0xFF0F1220),
    primary = Color(0xFFA79BE8),
    onPrimary = Color(0xFF14102B),
    onBackground = Color(0xFFE3E4EC),
    onSurface = Color(0xFFE3E4EC),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = PolnocColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TonightScreen()
                }
            }
        }
    }
}

/** Minimal "Noc" screen: start and stop listening. The full UI follows the web prototype later. */
@Composable
private fun TonightScreen() {
    val status by ListeningState.status.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Notifications are optional; the microphone is not.
        if (result[Manifest.permission.RECORD_AUDIO] == true) ListeningService.start(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (status.listening) "Nasłuch aktywny" else "Spokojnej nocy",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (status.listening) {
            Text("Wykryto zdarzeń: ${status.eventCount}", color = MaterialTheme.colorScheme.onBackground)
            status.levelDb?.let { Text("Poziom: ${it.roundToInt()} dBFS", color = MaterialTheme.colorScheme.onBackground) }
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
    }
}
