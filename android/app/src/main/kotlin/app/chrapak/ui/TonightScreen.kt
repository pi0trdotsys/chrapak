package app.chrapak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chrapak.R
import app.chrapak.schedule.AutoListenPrefs
import app.chrapak.schedule.ScheduleMath
import app.chrapak.service.ListeningService
import app.chrapak.service.ListeningState
import app.chrapak.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** "Noc": one big button to listen, what is happening now, and whether the phone is ready for the night. */
@Composable
fun TonightScreen(onOpenSettings: () -> Unit) {
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
    val toggle = {
        when {
            status.listening -> ListeningService.stop(context)
            permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } -> ListeningService.start(context)
            else -> launcher.launch(permissions)
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(status.listening, status.armed)
        ListenButton(listening = status.listening, onClick = toggle)
        StatusBlock(status.listening, status.sinceMs, status.eventCount, status.classifierActive, status.levelDb)
        status.error?.let { Text(it, color = Palette.destructive, textAlign = TextAlign.Center) }
        StandbyCard(status.armed, onOpenSettings)
        ReadinessCard(status.listening, onOpenSettings)
        Text(
            "Dźwięk jest analizowany na telefonie. Nic nie wychodzi poza urządzenie.",
            color = Palette.mutedForeground,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(listening: Boolean, armed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_stat_chrapak),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Palette.primary),
                modifier = Modifier.size(30.dp),
            )
            Text("chrapak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        val (dot, text) = when {
            listening -> Palette.teal to "Nasłuch aktywny"
            armed -> Palette.primary to "Czuwam"
            else -> Palette.mutedForeground to "Spokojnej nocy"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(8.dp)) { drawCircle(dot) }
            Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.mutedForeground)
        }
    }
}

/** The main control: a button with a slow "breathing" halo while listening. */
@Composable
private fun ListenButton(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val phase = if (listening) breath else 0f
    val primary = Palette.primary
    val onPrimary = Palette.onPrimary
    val soft = Palette.softPrimary
    val label = if (listening) "Zatrzymaj nasłuch" else "Rozpocznij nasłuch"
    Box(
        Modifier
            .size(240.dp)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension / 2
            // Halo rings widen and fade as the button "breathes"; at rest they sit still and faint.
            drawCircle(soft, radius = base * (0.92f + 0.08f * phase), center = c)
            drawCircle(primary.copy(alpha = if (listening) 0.10f + 0.12f * (1 - phase) else 0.08f), radius = base * (0.80f + 0.12f * phase), center = c)
            drawCircle(primary.copy(alpha = 0.35f), radius = base * 0.64f, center = c, style = Stroke(width = 2.dp.toPx()))
            drawCircle(primary, radius = base * (0.58f + 0.015f * phase), center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (listening) "Zatrzymaj" else "Rozpocznij", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onPrimary)
            Text("nasłuch", style = MaterialTheme.typography.titleMedium, color = onPrimary.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StatusBlock(listening: Boolean, sinceMs: Long?, events: Int, classifier: Boolean, levelDb: Float?) {
    // Re-read the clock every 30 s so the elapsed time moves while the screen is on.
    val now by produceState(System.currentTimeMillis(), listening, sinceMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (listening && sinceMs != null) {
            Text(durationLabel((now - sinceMs).coerceAtLeast(0)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("od ${timeLabel(sinceMs)}", color = Palette.mutedForeground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (events == 1) "1 epizod" else "$events epizodów")
                Chip(if (classifier) "klasyfikator: aktywny" else "klasyfikator: brak modelu")
            }
        } else {
            Text("Gotowy do nasłuchu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Połóż telefon przy łóżku i dotknij przycisku.", color = Palette.mutedForeground, textAlign = TextAlign.Center)
        }
        LevelBars(levelDb, listening)
    }
}

@Composable
private fun Chip(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Palette.mutedForeground)
    }
}

/** A calm "I can hear the room" indicator: bars that sway gently and rise with the room's level. */
@Composable
private fun LevelBars(levelDb: Float?, listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "bars")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t")
    val color = if (listening) Palette.primary else Palette.border
    // -90 dBFS is an empty room, -45 dBFS is snoring close to the phone.
    val level = if (listening && levelDb != null) ((levelDb + 90f) / 45f).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp)) {
        val bars = 28
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val sway = 0.5f + 0.5f * sin((t * 2 * PI + i * 0.45).toFloat())
            val h = size.height * (0.12f + (if (listening) 0.88f * (0.25f + 0.75f * level) * (0.35f + 0.65f * sway) else 0f))
            drawRoundRect(color, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable
private fun StandbyCard(armed: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (armed) "Czuwanie włączone" else "Czuwanie wyłączone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val window = if (AutoListenPrefs.scheduleOn(context)) {
                "Nasłuch włączy się sam od ${ScheduleMath.format(AutoListenPrefs.startMin(context))} do ${ScheduleMath.format(AutoListenPrefs.endMin(context))}."
            } else "Nasłuch włączysz ręcznie."
            Text(
                if (armed) window + if (AutoListenPrefs.napDetect(context)) " Drzemki w ciągu dnia wykrywam po bezruchu telefonu." else ""
                else "Włącz czuwanie w Ustawieniach, a nasłuch będzie startował sam, bez otwierania aplikacji.",
                color = Palette.mutedForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenSettings) { Text(if (armed) "Zmień w Ustawieniach" else "Włącz w Ustawieniach") }
        }
    }
}

/** What the phone needs before a night: the things that, if missing, make listening quietly fail. */
@Composable
private fun ReadinessCard(listening: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    // Re-evaluated whenever listening starts or stops, which is when this screen is looked at.
    val micOk = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val charging = context.getSystemService(BatteryManager::class.java).isCharging
    val items = listOf(
        Triple("Mikrofon", micOk, "Zezwól w ustawieniach aplikacji"),
        Triple("Powiadomienia", notifOk, "Bez nich nie zobaczysz, że czuwam"),
        Triple("Wyjątek od oszczędzania baterii", batteryOk, "Bez niego telefon może zgubić start nasłuchu"),
        Triple("Telefon na ładowarce", charging, "Na całą noc lepiej podłączyć"),
    )
    val missing = items.count { !it.second }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Palette.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (missing == 0) "Wszystko gotowe na noc" else "Do poprawy przed nocą: $missing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val okColor = Palette.teal
            val attentionColor = Palette.amber
            items.forEach { (label, ok, hint) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.padding(top = 6.dp).size(10.dp)) { drawCircle(if (ok) okColor else attentionColor) }
                    Column {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (!ok) Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.mutedForeground)
                    }
                }
            }
            if (missing > 0 && !listening) OutlinedButton(onClick = onOpenSettings) { Text("Otwórz Ustawienia") }
        }
    }
}
