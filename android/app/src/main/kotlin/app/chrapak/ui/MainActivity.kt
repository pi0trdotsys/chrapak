package app.chrapak.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.chrapak.data.ChrapakDatabase
import app.chrapak.data.NightRepository
import app.chrapak.health.HealthConnectSource
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.ChrapakColors

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        // The standby service dies with the process (restart, update, cleaner apps). Opening the app is the
        // moment Android allows it to come back, so restore it whenever standby is still switched on.
        if (AutoListenPrefs.armed(this) && !ListeningState.status.value.armed) ListeningService.arm(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val health = HealthConnectSource(applicationContext)
        val repo = NightRepository(applicationContext, ChrapakDatabase.get(applicationContext), health)
        setContent {
            MaterialTheme(colorScheme = ChrapakColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(repo, health)
                }
            }
        }
    }
}
