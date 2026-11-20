package app.chrapak.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Every colour the app draws with. Screens read them through [Palette], so switching a theme
 * recolours everything, including the Canvas charts, without touching the screens.
 * The first five are the web prototype's themes, converted from its OKLCH tokens.
 */
@Immutable
data class ChrapakPalette(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val card: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val destructive: Color,
    val teal: Color,
    val amber: Color,
    val sleepDeep: Color,
    val sleepLight: Color,
    val sleepAwake: Color,
    val heart: Color,
    val softPrimary: Color,
)

val PolnocPalette = ChrapakPalette(
    isDark = true,
    background = Color(0xFF0A0C13),
    foreground = Color(0xFFE3E4EB),
    card = Color(0xFF12151F),
    muted = Color(0xFF1A1C26),
    mutedForeground = Color(0xFF8D909C),
    border = Color(0xFF282B37),
    primary = Color(0xFF9E9BE5),
    onPrimary = Color(0xFF111224),
    destructive = Color(0xFFF58B87),
    teal = Color(0xFF64C1B2),
    amber = Color(0xFFE2AD5D),
    sleepDeep = Color(0xFF4A4B84),
    sleepLight = Color(0xFF7274AD),
    sleepAwake = Color(0xFFABACD9),
    heart = Color(0xFF7C869D),
    softPrimary = Color(0xFF1C1C34),
)

val DzienPalette = ChrapakPalette(
    isDark = false,
    background = Color(0xFFF9F6F2),
    foreground = Color(0xFF1C1F2A),
    card = Color(0xFFFFFFFF),
    muted = Color(0xFFF1EEE8),
    mutedForeground = Color(0xFF5F6370),
    border = Color(0xFFE1DED6),
    primary = Color(0xFF5D52B4),
    onPrimary = Color(0xFFF8F8FC),
    destructive = Color(0xFFC92F33),
    teal = Color(0xFF007979),
    amber = Color(0xFFBA6900),
    sleepDeep = Color(0xFF48408B),
    sleepLight = Color(0xFF8380C3),
    sleepAwake = Color(0xFFBCBBED),
    heart = Color(0xFF697284),
    softPrimary = Color(0xFFE5E5FE),
)

val ZarPalette = ChrapakPalette(
    isDark = true,
    background = Color(0xFF110908),
    foreground = Color(0xFFEEE6DE),
    card = Color(0xFF1C1211),
    muted = Color(0xFF271A19),
    mutedForeground = Color(0xFFA39188),
    border = Color(0xFF392928),
    primary = Color(0xFFE49693),
    onPrimary = Color(0xFF250E0D),
    destructive = Color(0xFFF47C6E),
    teal = Color(0xFF70C3B3),
    amber = Color(0xFFE8B45E),
    sleepDeep = Color(0xFF783B3C),
    sleepLight = Color(0xFFA76663),
    sleepAwake = Color(0xFFDBAAA1),
    heart = Color(0xFFA3847E),
    softPrimary = Color(0xFF331918),
)

val LasPalette = ChrapakPalette(
    isDark = true,
    background = Color(0xFF040E09),
    foreground = Color(0xFFE3EAE4),
    card = Color(0xFF0A1812),
    muted = Color(0xFF13221B),
    mutedForeground = Color(0xFF86978B),
    border = Color(0xFF20322A),
    primary = Color(0xFF9CC685),
    onPrimary = Color(0xFF051C0A),
    destructive = Color(0xFFF2897C),
    teal = Color(0xFF64C3C8),
    amber = Color(0xFFE6B55D),
    sleepDeep = Color(0xFF24603D),
    sleepLight = Color(0xFF51895E),
    sleepAwake = Color(0xFFA0C398),
    heart = Color(0xFF759181),
    softPrimary = Color(0xFF102816),
)

val OledPalette = ChrapakPalette(
    isDark = true,
    background = Color(0xFF000000),
    foreground = Color(0xFFDCDEE2),
    card = Color(0xFF090A0C),
    muted = Color(0xFF101214),
    mutedForeground = Color(0xFF888C94),
    border = Color(0xFF202226),
    primary = Color(0xFFA5D0F6),
    onPrimary = Color(0xFF05101C),
    destructive = Color(0xFFF47B74),
    teal = Color(0xFF71CBB9),
    amber = Color(0xFFE8B45E),
    sleepDeep = Color(0xFF324E73),
    sleepLight = Color(0xFF5A7EA3),
    sleepAwake = Color(0xFFA4C2DC),
    heart = Color(0xFF808693),
    softPrimary = Color(0xFF0B1723),
)

val LocalPalette = staticCompositionLocalOf { PolnocPalette }

/** `Palette.card` etc. resolve against the palette of the current theme. */
object Palette {
    val background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val foreground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.foreground
    val card: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val mutedForeground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mutedForeground
    val border: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.border
    val primary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.primary
    val onPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onPrimary
    val destructive: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.destructive
    val teal: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.teal
    val amber: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amber
    val sleepDeep: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sleepDeep
    val sleepLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sleepLight
    val sleepAwake: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sleepAwake
    val heart: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heart
    val softPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.softPrimary
}

/** What the user can pick in Settings. */
enum class ThemeChoice(val label: String, val description: String) {
    SYSTEM("Systemowy", "Jasny w dzień, ciemny w nocy, zgodnie z telefonem"),
    LIGHT("Jasny", "Papierowe tło, fioletowy akcent. Do porannego raportu"),
    DARK("Ciemny", "Granat i fiolet. Domyślny"),
    MATERIAL_YOU("Material You", "Kolory z tapety telefonu (Android 12+)"),
    EMBER("Żar", "Ciepły brąz i róż, mało niebieskiego światła"),
    FOREST("Las", "Głęboka zieleń i szałwia"),
    OLED("Czerń OLED", "Czysta czerń, oszczędza baterię na ekranie OLED"),
}

/** The chosen theme, kept in memory for recomposition and in preferences across launches. */
object ThemeState {
    private const val FILE = "appearance"
    private const val KEY = "theme"

    var choice by mutableStateOf(ThemeChoice.DARK)
        private set

    fun load(context: Context) {
        val saved = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        choice = ThemeChoice.entries.firstOrNull { it.name == saved } ?: ThemeChoice.DARK
    }

    fun set(context: Context, value: ThemeChoice) {
        choice = value
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
    }
}

/** Whether Material You is available here; below Android 12 it falls back to the system light or dark theme. */
fun materialYouAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The palette a choice resolves to right now. */
@Composable
fun paletteFor(choice: ThemeChoice): ChrapakPalette {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    return when (choice) {
        ThemeChoice.SYSTEM -> if (systemDark) PolnocPalette else DzienPalette
        ThemeChoice.LIGHT -> DzienPalette
        ThemeChoice.DARK -> PolnocPalette
        ThemeChoice.EMBER -> ZarPalette
        ThemeChoice.FOREST -> LasPalette
        ThemeChoice.OLED -> OledPalette
        ThemeChoice.MATERIAL_YOU ->
            if (materialYouAvailable()) {
                materialYouPalette(if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), systemDark)
            } else if (systemDark) PolnocPalette else DzienPalette
    }
}

/**
 * Material You supplies the surfaces and the accent. The data colours (SpO2, loud snoring) stay
 * fixed per light or dark, so a chart means the same thing whatever the wallpaper.
 */
fun materialYouPalette(scheme: ColorScheme, dark: Boolean): ChrapakPalette = ChrapakPalette(
    isDark = dark,
    background = scheme.background,
    foreground = scheme.onBackground,
    card = scheme.surfaceContainer,
    muted = scheme.surfaceContainerHigh,
    mutedForeground = scheme.onSurfaceVariant,
    border = scheme.outlineVariant,
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    destructive = scheme.error,
    teal = if (dark) Color(0xFF64C1B2) else Color(0xFF1B7F70),
    amber = if (dark) Color(0xFFE2AD5D) else Color(0xFF9B6A0E),
    sleepDeep = lerp(scheme.primary, scheme.background, 0.65f),
    sleepLight = lerp(scheme.primary, scheme.background, 0.35f),
    sleepAwake = lerp(scheme.primary, scheme.onBackground, 0.35f),
    heart = scheme.outline,
    softPrimary = scheme.primaryContainer,
)

fun ChrapakPalette.toColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            background = background, onBackground = foreground,
            surface = card, onSurface = foreground,
            surfaceVariant = muted, onSurfaceVariant = mutedForeground,
            surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = muted,
            primary = primary, onPrimary = onPrimary,
            secondary = sleepLight, secondaryContainer = softPrimary, onSecondaryContainer = foreground,
            outline = border, outlineVariant = border, error = destructive,
        )
    } else {
        lightColorScheme(
            background = background, onBackground = foreground,
            surface = card, onSurface = foreground,
            surfaceVariant = muted, onSurfaceVariant = mutedForeground,
            surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = muted,
            primary = primary, onPrimary = onPrimary,
            secondary = sleepLight, secondaryContainer = softPrimary, onSecondaryContainer = foreground,
            outline = border, outlineVariant = border, error = destructive,
        )
    }

@Composable
fun ChrapakTheme(choice: ThemeChoice = ThemeState.choice, content: @Composable () -> Unit) {
    val palette = paletteFor(choice)
    val view = LocalView.current
    // System bar icons must contrast with the palette, or a light theme hides the clock.
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !palette.isDark
        controller.isAppearanceLightNavigationBars = !palette.isDark
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}
