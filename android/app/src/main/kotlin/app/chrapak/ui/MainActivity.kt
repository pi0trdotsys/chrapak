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
import app.chrapak.ui.theme.ChrapakColors

class MainActivity : ComponentActivity() {
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
