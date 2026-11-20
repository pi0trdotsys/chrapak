package app.chrapak.ui.settings

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.Palette
import app.chrapak.widget.StatusWidget

/** Shows the home screen widget as it looks right now, in both sizes, and asks the launcher to place it. */
@Composable
fun WidgetCard() {
    val context = LocalContext.current
    val status by ListeningState.status.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    // Rebuilt whenever what the widget shows changes, so the preview is never stale.
    val content = remember(status.listening, status.armed, status.eventCount, status.sinceMs) { StatusWidget.content(context) }

    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Widżet na ekran główny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pokazuje, czy słucham, czuwam albo jestem wyłączony. Dotknięcie otwiera aplikację. Ma rozmiary 1×1 i 2×1.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RemotePreview({ StatusWidget.small(context, content) }, 76.dp, 76.dp, content)
                RemotePreview({ StatusWidget.wide(context, content) }, 190.dp, 76.dp, content)
            }
            Button(
                onClick = {
                    message = if (StatusWidget.requestPin(context)) "Potwierdź dodanie w oknie ekranu głównego."
                    else "Ten ekran główny nie pozwala dodać widżetu z aplikacji. Dodaj go z menu widżetów, szukając „Chrapak”."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj na ekran główny") }
            message?.let { Text(it, color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Draws a RemoteViews layout inside the app, the way the launcher would. */
@Composable
private fun RemotePreview(build: () -> android.widget.RemoteViews, width: Dp, height: Dp, key: Any) {
    AndroidView(
        modifier = Modifier.width(width).height(height),
        factory = { FrameLayout(it) },
        update = { frame ->
            frame.removeAllViews()
            frame.addView(build().apply(frame.context, frame))
        },
    )
}
