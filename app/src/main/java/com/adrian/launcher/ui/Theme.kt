package com.adrian.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.adrian.launcher.R
import java.time.LocalDateTime
import java.util.Locale

val Ink = Color(0xFFF2F2F2)
val Quiet = Color(0xFFB4B4B4)
val Faint = Color(0xFF8C8C8C)
val Line = Color(0xFF2C2F33)
val Panel = Color(0xFF16181A)
val DefaultAccent = Color(0xFFFF8A3D)

/** Accent choices offered in settings. */
val AccentOptions = listOf(
    Color(0xFFFF8A3D),
    Color(0xFF7CC4FF),
    Color(0xFFB8F28A),
    Color(0xFFF2F2F2),
)

val Polish: Locale = Locale.forLanguageTag("pl-PL")

val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

private fun TextStyle.outfit() = copy(fontFamily = Outfit)

private val OutfitTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.outfit(),
        displayMedium = t.displayMedium.outfit(),
        displaySmall = t.displaySmall.outfit(),
        headlineLarge = t.headlineLarge.outfit(),
        headlineMedium = t.headlineMedium.outfit(),
        headlineSmall = t.headlineSmall.outfit(),
        titleLarge = t.titleLarge.outfit(),
        titleMedium = t.titleMedium.outfit(),
        titleSmall = t.titleSmall.outfit(),
        bodyLarge = t.bodyLarge.outfit(),
        bodyMedium = t.bodyMedium.outfit(),
        bodySmall = t.bodySmall.outfit(),
        labelLarge = t.labelLarge.outfit(),
        labelMedium = t.labelMedium.outfit(),
        labelSmall = t.labelSmall.outfit(),
    )
}

@Composable
fun LauncherTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            background = Color.Black,
            onBackground = Ink,
            surface = Panel,
            onSurface = Ink,
            surfaceVariant = Panel,
            onSurfaceVariant = Quiet,
            surfaceContainer = Panel,
            surfaceContainerHigh = Panel,
            outline = Line,
        ),
        typography = OutfitTypography,
        content = content,
    )
}

/** Polish date words; the JDK forms differ between Android versions, so they are spelled out here. */
object PolishDate {
    private val monthsGenitive = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )
    private val monthsNominative = listOf(
        "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
        "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień",
    )
    private val monthsShort = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
    private val daysShort = listOf("pon", "wt", "śr", "czw", "pt", "sob", "nd")
    private val daysLong = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")

    fun monthGenitive(t: LocalDateTime) = monthsGenitive[t.monthValue - 1]
    fun monthNominative(t: LocalDateTime) = monthsNominative[t.monthValue - 1]
    fun monthShort(t: LocalDateTime) = monthsShort[t.monthValue - 1]
    fun dayShort(t: LocalDateTime) = daysShort[t.dayOfWeek.value - 1]
    fun dayLong(t: LocalDateTime) = daysLong[t.dayOfWeek.value - 1]

    fun greeting(t: LocalDateTime): String =
        if (t.hour >= 18 || t.hour < 4) "Dobry wieczór" else "Dzień dobry"
}
