package app.chrapak.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.ui.theme.Palette

/**
 * Automatic listening needs the phone to keep the app's alarms working. Phones, Xiaomi's in
 * particular, put an app they consider idle on hold and delay or drop its alarms, so the user
 * has to exempt Chrapak from battery optimisation.
 */
@Composable
fun BatteryCard() {
    val context = LocalContext.current
    val power = context.getSystemService(PowerManager::class.java)
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exempt = power.isIgnoringBatteryOptimizations(context.packageName)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Oszczędzanie baterii", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (exempt) {
                Text("Chrapak jest wyłączony z oszczędzania baterii, więc czuwanie może startować o czasie.", color = Palette.teal)
            } else {
                Text(
                    "Telefon może wstrzymać czuwanie i opóźnić lub zgubić alarm startu. Bez wyjątku automatyczny nasłuch nie jest wiarygodny.",
                    color = Palette.amber,
                )
                Button(
                    onClick = {
                        launcher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zezwól na pracę w tle") }
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Otwórz ustawienia aplikacji") }
            Text(
                "Na Xiaomi (HyperOS) w ustawieniach aplikacji włącz też Autostart i ustaw oszczędzanie baterii na „Bez ograniczeń”.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
