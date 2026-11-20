package app.chrapak.ui.events

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chrapak.analysis.ClipAnalysis
import app.chrapak.analysis.ClipAnalyzer
import app.chrapak.audio.WavReader
import app.chrapak.data.SnoreEventEntity
import app.chrapak.detection.YamnetClassifier
import app.chrapak.ui.classNamePl
import app.chrapak.ui.dbLabel
import app.chrapak.ui.decimal
import app.chrapak.ui.durationLabel
import app.chrapak.ui.theme.LocalPalette
import app.chrapak.ui.theme.Palette
import app.chrapak.ui.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** What YAMNet heard over the clip: chance of snoring second by second, and the sounds that stood out. */
private class ModelView(val snoring: List<Float>, val top: List<Pair<String, Float>>)

private class Loaded(val analysis: ClipAnalysis, val model: ModelView?)

/** Opens one clip and reads it: loudness, rhythm, pauses, spectrum and, when the model is present, what it hears. */
@Composable
fun ClipAnalysisScreen(event: SnoreEventEntity, file: File?, onBack: () -> Unit) {
    val context = LocalContext.current
    val loaded by produceState<Loaded?>(null, event.id) {
        value = file?.let { f ->
            withContext(Dispatchers.Default) {
                val wav = WavReader.read(f)
                Loaded(ClipAnalyzer.analyze(wav.samples, wav.sampleRate), modelView(context, wav.samples))
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Wróć") }
        Column {
            Text("ANALIZA NAGRANIA", style = MaterialTheme.typography.labelSmall, color = Palette.mutedForeground)
            Text("Epizod o ${timeLabel(event.startMs)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${durationLabel(event.durationMs)} · szczyt ${dbLabel(event.peakDb)} · pewność ${(event.confidence * 100).roundToInt()}%", color = Palette.mutedForeground)
        }
        val result = loaded
        when {
            file == null -> Text("Brak pliku audio dla tego epizodu.", color = Palette.mutedForeground)
            result == null -> Text("Analizuję nagranie…", color = Palette.mutedForeground)
            else -> {
                WaveformCard(result.analysis)
                Metrics(result.analysis)
                BandsCard(result.analysis)
                result.model?.let { ModelCard(it, result.analysis.durationMs) }
                    ?: Text("Model YAMNet nie jest zainstalowany, więc pomijam analizę dźwięku. Pobierz go skryptem fetch-yamnet.sh.", color = Palette.mutedForeground, style = MaterialTheme.typography.bodySmall)
                Text(
                    "To obserwacje z jednego nagrania, nie diagnoza. Jeśli widzisz długie przerwy między chrapnięciami albo budzisz się z dusznością, porozmawiaj z lekarzem.",
                    color = Palette.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Slides a one-second hop over the clip and averages what the model hears. Skipped when the model is missing. */
private fun modelView(context: android.content.Context, samples: ShortArray): ModelView? {
    val classifier = YamnetClassifier.load(context) ?: return null
    return classifier.use { c ->
        val window = c.windowSamples
        val hop = 16_000
        val starts = generateSequence(0) { it + hop }.takeWhile { it + window <= samples.size }.take(120).toList()
        if (starts.isEmpty()) return null
        val sums = FloatArray(c.classLabels.size)
        val snoring = starts.map { start ->
            val p = c.probabilities(samples.copyOfRange(start, start + window))
            for (i in p.indices) sums[i] += p[i]
            p[c.snoringClassIndex]
        }
        val top = sums.indices.sortedByDescending { sums[it] }.take(5).map { c.classLabels[it] to sums[it] / starts.size }
        ModelView(snoring, top)
    }
}

@Composable
private fun WaveformCard(a: ClipAnalysis) {
    val colors = LocalPalette.current
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Przebieg nagrania", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                // Shaded spans mark the stretches found as snoring; the bars are the loudness over time.
                a.bursts.forEach { b ->
                    val x = b.startMs.toFloat() / a.durationMs * size.width
                    val w = (b.durationMs.toFloat() / a.durationMs * size.width).coerceAtLeast(2f)
                    drawRect(colors.softPrimary, Offset(x, 0f), Size(w, size.height))
                }
                val n = a.waveform.size
                val gap = 2.dp.toPx()
                val bar = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                a.waveform.forEachIndexed { i, level ->
                    val h = (size.height * level).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(colors.primary, Offset(i * (bar + gap), (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
                }
            }
            Text("Zacieniowane fragmenty to wykryte chrapnięcia: ${a.bursts.size}.", style = MaterialTheme.typography.bodySmall, color = Palette.mutedForeground)
        }
    }
}

@Composable
private fun Metrics(a: ClipAnalysis) {
    val cells = listOf(
        "Chrapnięć w nagraniu" to "${a.bursts.size}",
        "Rytm" to (a.burstsPerMinute?.let { "${decimal(it)} / min" } ?: "–"),
        "Najdłuższa przerwa" to (a.longestPauseMs?.let { durationLabel(it) } ?: "–"),
        "Najgłośniejsze" to (a.bursts.maxOfOrNull { it.peakDb }?.let { dbLabel(it) } ?: "–"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Palette.card)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.mutedForeground)
                            Text(value, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandsCard(a: ClipAnalysis) {
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Gdzie jest energia dźwięku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            a.bandShare.forEach { band ->
                ShareBar(band.label, band.share)
            }
            Text("Chrapanie mieści się zwykle poniżej 1 kHz.", style = MaterialTheme.typography.bodySmall, color = Palette.mutedForeground)
        }
    }
}

@Composable
private fun ModelCard(m: ModelView, durationMs: Long) {
    val colors = LocalPalette.current
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Co słyszy model (YAMNet)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Szansa na chrapanie, sekunda po sekundzie", style = MaterialTheme.typography.bodySmall, color = Palette.mutedForeground)
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                if (m.snoring.size > 1) {
                    val path = Path()
                    m.snoring.forEachIndexed { i, p ->
                        val x = i.toFloat() / (m.snoring.size - 1) * size.width
                        val y = size.height * (1 - p.coerceIn(0f, 1f))
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, colors.amber, style = Stroke(width = 2.dp.toPx()))
                }
            }
            Text("Najczęstsze dźwięki", style = MaterialTheme.typography.labelLarge, color = Palette.mutedForeground)
            m.top.forEach { (label, share) -> ShareBar(classNamePl(label), share) }
        }
    }
}

@Composable
private fun ShareBar(label: String, share: Float) {
    val colors = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = Palette.mutedForeground)
        }
        Box(Modifier.fillMaxWidth().height(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawRoundRect(colors.border, Offset.Zero, size, CornerRadius(3.dp.toPx()))
                drawRoundRect(colors.primary, Offset.Zero, Size(size.width * share.coerceIn(0f, 1f), size.height), CornerRadius(3.dp.toPx()))
            }
        }
    }
}
