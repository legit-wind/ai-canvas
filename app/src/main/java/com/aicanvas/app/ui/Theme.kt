package com.aicanvas.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"
/** Pure black page for OLED screens (dark colours, black background). */
const val THEME_OLED = "oled"
/** Text size choices in Settings → Appearance, as a multiplier on the phone's own font size. */
val TEXT_SIZES = listOf("small" to 0.9f, "default" to 1f, "large" to 1.15f)

/** Accent choices in Settings → Appearance. "default" is near-black (white in dark mode). */
val ACCENTS = listOf("default", "blue", "indigo", "purple", "pink", "orange", "green", "teal")

/**
 * App colours. [AICanvasTheme] sets [dark] and [accent] from the user's Appearance settings and also feeds the
 * same values into MaterialTheme.colorScheme, so Material components and our own drawing always agree.
 */
object C {
    var dark by mutableStateOf(false)
    var accent by mutableStateOf("default")
    var oled by mutableStateOf(false)
    /** A soft off-white (or deep blue-black) page, so white cards stand out gently. */
    val Bg get() = if (dark && oled) Color.Black else if (dark) Color(0xFF0F1115) else Color(0xFFF4F5F9)
    val Card get() = if (dark && oled) Color(0xFF121214) else if (dark) Color(0xFF1B1D23) else Color(0xFFFFFFFF)
    /** A step from [Card]: fields and chips inside a card. */
    val Raised get() = if (dark && oled) Color(0xFF1E1F23) else if (dark) Color(0xFF282B33) else Color(0xFFF0F1F5)
    val Line get() = if (dark) Color(0xFF353943) else Color(0xFFE1E3EA)
    val Text get() = if (dark) Color(0xFFF2F2F7) else Color(0xFF111111)
    val Muted get() = if (dark) Color(0xFF98989F) else Color(0xFF6E6E73)
    /** "default" is a calm indigo; other accents come from the palette. */
    val Accent get() = if (accent == "default") (if (dark) Color(0xFFA5B0FF) else Color(0xFF4F5BD5))
        else paletteColor(accent).let { if (dark) lighten(it) else it }
    val OnAccent get() = if (accent == "default" && dark) Color(0xFF111111) else Color.White
    val Bad get() = if (dark) Color(0xFFFF6369) else Color(0xFFD93036)
    val Good get() = if (dark) Color(0xFF4ADE80) else Color(0xFF1E8E4E)
    /** Dims the screen behind a sheet. */
    val Scrim get() = Color.Black.copy(alpha = if (dark) 0.5f else 0.2f)

    private fun lighten(c: Color) = Color(
        red = c.red + (1 - c.red) * 0.2f, green = c.green + (1 - c.green) * 0.2f, blue = c.blue + (1 - c.blue) * 0.2f,
    )
}

/** One type scale for the whole app; every size is in sp so it grows with the phone's font size. */
val AppType = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AICanvasTheme(mode: String = THEME_SYSTEM, accent: String = "default", textSize: String = "default", content: @Composable () -> Unit) {
    val dark = when (mode) { THEME_DARK, THEME_OLED -> true; THEME_LIGHT -> false; else -> isSystemInDarkTheme() }
    if (C.dark != dark) C.dark = dark
    val oled = mode == THEME_OLED
    if (C.oled != oled) C.oled = oled
    if (C.accent != accent) C.accent = accent

    // Status and navigation bar icons follow the chosen theme, not just the phone's.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activity?.window?.decorView?.setBackgroundColor(C.Bg.toArgb())
    }

    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = C.Accent,
            onPrimary = C.OnAccent,
            secondaryContainer = C.Card,
            onSecondaryContainer = C.Text,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Bg,
            onSurface = C.Text,
            onSurfaceVariant = C.Muted,
            surfaceVariant = C.Card,
            surfaceContainer = C.Card,
            surfaceContainerLow = C.Card,
            surfaceContainerHigh = C.Raised,
            surfaceContainerHighest = C.Raised,
            outline = C.Line,
            outlineVariant = C.Line,
            error = C.Bad,
        ),
        typography = AppType,
    ) {
        val scale = TEXT_SIZES.firstOrNull { it.first == textSize }?.second ?: 1f
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * scale),
            content = content,
        )
    }
}

/** Readable text/icon colour on top of [c]: near-black on light colours, white on dark ones. */
fun onColor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF111111) else Color.White

/**
 * The six card tones the AI can pick. Each has an [ink] (icons, bars, rings, the header strip) and a soft [tint]
 * (card background). Both are tuned separately for light and dark so text stays readable.
 */
object Tones {
    val ALL = listOf("blue", "green", "amber", "rose", "violet", "teal")

    /** Older or looser names the AI might use, folded into the six. */
    fun canonical(tone: String?): String? = when (tone?.lowercase()?.trim()) {
        "blue", "indigo", "sky" -> "blue"
        "green", "mint" -> "green"
        "amber", "orange", "yellow" -> "amber"
        "rose", "red", "pink" -> "rose"
        "violet", "purple" -> "violet"
        "teal", "cyan" -> "teal"
        else -> null
    }

    fun ink(tone: String?): Color? = when (canonical(tone)) {
        "blue" -> if (C.dark) Color(0xFF8AB4FF) else Color(0xFF2F6FDB)
        "green" -> if (C.dark) Color(0xFF6FD69A) else Color(0xFF1F8A4C)
        "amber" -> if (C.dark) Color(0xFFF5C26B) else Color(0xFFB7700A)
        "rose" -> if (C.dark) Color(0xFFFF8FA3) else Color(0xFFD03A5C)
        "violet" -> if (C.dark) Color(0xFFC4A5FF) else Color(0xFF7A4FD6)
        "teal" -> if (C.dark) Color(0xFF5FD4CC) else Color(0xFF0E8C86)
        else -> null
    }

    fun tint(tone: String?): Color = ink(tone)?.copy(alpha = if (C.dark) 0.14f else 0.08f)?.compositeOver(C.Card) ?: C.Card
}

/** The ink colour of the card being drawn, so blocks inside it pick up the card's tone. */
val LocalInk = staticCompositionLocalOf { Color.Unspecified }

/** The current card's ink, or the app accent outside a toned card. */
@Composable
@ReadOnlyComposable
fun ink(): Color = LocalInk.current.takeIf { it != Color.Unspecified } ?: C.Accent
