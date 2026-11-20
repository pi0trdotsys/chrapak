package app.chrapak.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** "Północ" tokens from the web prototype (`src/styles.css`), converted to sRGB. */
object Palette {
    val background = Color(0xFF0A0C13)
    val foreground = Color(0xFFE3E4EB)
    val card = Color(0xFF12151F)
    val muted = Color(0xFF1A1C26)
    val mutedForeground = Color(0xFF8D909C)
    val border = Color(0xFF282B37)
    val primary = Color(0xFF9E9BE5)
    val onPrimary = Color(0xFF111224)
    val secondary = Color(0xFF212235)
    val destructive = Color(0xFFF58B87)
    val teal = Color(0xFF64C1B2)
    val amber = Color(0xFFE2AD5D)
    val sleepDeep = Color(0xFF4A4B84)
    val sleepLight = Color(0xFF7274AD)
    val sleepAwake = Color(0xFFABACD9)
    val heart = Color(0xFF7C869D)
    val softPrimary = Color(0xFF1C1C34)
}

val ChrapakColors = darkColorScheme(
    background = Palette.background,
    onBackground = Palette.foreground,
    surface = Palette.card,
    onSurface = Palette.foreground,
    surfaceVariant = Palette.muted,
    onSurfaceVariant = Palette.mutedForeground,
    surfaceContainer = Palette.card,
    surfaceContainerHigh = Palette.card,
    primary = Palette.primary,
    onPrimary = Palette.onPrimary,
    secondary = Palette.sleepLight,
    secondaryContainer = Palette.softPrimary,
    onSecondaryContainer = Palette.foreground,
    outline = Palette.border,
    outlineVariant = Palette.border,
    error = Palette.destructive,
)
