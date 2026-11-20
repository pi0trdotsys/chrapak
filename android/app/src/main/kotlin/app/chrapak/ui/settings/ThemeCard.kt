package app.chrapak.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.ui.theme.ChrapakPalette
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.theme.ThemeChoice
import app.chrapak.ui.theme.ThemeState
import app.chrapak.ui.theme.materialYouAvailable
import app.chrapak.ui.theme.paletteFor

/** Theme picker: system, light, dark, Material You, and the prototype's extra themes. */
@Composable
fun ThemeCard() {
    val context = LocalContext.current
    val current = ThemeState.choice
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Wygląd", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ThemeChoice.entries.forEach { choice ->
                val enabled = choice != ThemeChoice.MATERIAL_YOU || materialYouAvailable()
                val palette = paletteFor(choice)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { ThemeState.set(context, choice) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RadioButton(selected = current == choice, onClick = { ThemeState.set(context, choice) }, enabled = enabled)
                    Column(Modifier.weight(1f)) {
                        Text(choice.label, fontWeight = FontWeight.Medium, color = if (enabled) Palette.foreground else Palette.mutedForeground)
                        Text(
                            if (enabled) choice.description else "Wymaga Androida 12 lub nowszego",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.mutedForeground,
                        )
                    }
                    Swatches(palette)
                }
            }
        }
    }
}

/** Four dots in the colours of a theme, so each option previews itself. */
@Composable
private fun Swatches(p: ChrapakPalette) {
    val outline = Palette.border
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(p.background, p.primary, p.teal, p.amber).forEach { color ->
            Canvas(Modifier.size(16.dp)) {
                drawCircle(color)
                drawCircle(outline, style = Stroke(width = 1.dp.toPx()), radius = size.minDimension / 2 - 0.5f, center = Offset(size.width / 2, size.height / 2))
            }
        }
    }
}
