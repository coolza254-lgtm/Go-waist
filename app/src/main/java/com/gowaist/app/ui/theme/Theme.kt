package com.gowaist.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowaist.app.data.settings.ThemeMode

/*
 * Design tokens: a calm cream page, ink outlines with a small offset shadow (a light 2D-game
 * touch) and two accents, terracotta for running and teal for progress. Every colour, radius
 * and text style used by the app comes from here.
 */

@Immutable
data class GameColors(
    /** Outline and offset shadow of cards and buttons. */
    val ink: Color,
    val run: Color,
    val goal: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val gold: Color,
    val card: Color,
    val line: Color,
    val track: Color,
    val muted: Color,
)

@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    /** Minimum height of tappable controls. */
    val touch: Dp = 52.dp,
    val bigTouch: Dp = 58.dp,
)

@Immutable
data class Radii(
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val pill: Dp = 100.dp,
)

object Palette {
    val Cream = Color(0xFFF4ECDD)
    val Paper = Color(0xFFFFF9EE)
    val Ink = Color(0xFF2A2420)
    val InkSoft = Color(0xFF6B6055)
    val Line = Color(0xFFD8CCB6)
    val Terracotta = Color(0xFFC9533A)
    val Teal = Color(0xFF3C7A6E)
    val Mustard = Color(0xFFB8860B)
    val Red = Color(0xFFB83A2E)

    // Muted chart colours that sit well on cream.
    val Blue = Color(0xFF3F6E9A)
    val Purple = Color(0xFF7A5C8E)
    val Pink = Color(0xFFC0566B)
    val Green = Color(0xFF5E8C4E)
    val Orange = Color(0xFFD9822B)
    val Yellow = Color(0xFFE9C46A)

    val Night = Color(0xFF1C1915)
    val NightCard = Color(0xFF28231E)
    val NightInk = Color(0xFFEFE6D6)
}

private val LightScheme = lightColorScheme(
    primary = Palette.Terracotta,
    onPrimary = Palette.Paper,
    primaryContainer = Color(0xFFF6DDD3),
    onPrimaryContainer = Color(0xFF4A160A),
    secondary = Palette.Teal,
    onSecondary = Palette.Paper,
    secondaryContainer = Color(0xFFD5E8E3),
    onSecondaryContainer = Color(0xFF0E2E28),
    tertiary = Palette.Mustard,
    onTertiary = Palette.Paper,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDE3D1),
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Color(0xFFF8F0E2),
    surfaceContainerHighest = Color(0xFFF1E7D6),
    surfaceContainerLow = Palette.Paper,
    surfaceContainerLowest = Palette.Paper,
    outline = Palette.Ink,
    outlineVariant = Palette.Line,
    error = Palette.Red,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE5745A),
    onPrimary = Color(0xFF2B0E06),
    primaryContainer = Color(0xFF5A2416),
    onPrimaryContainer = Color(0xFFF6DDD3),
    secondary = Color(0xFF6FB3A5),
    onSecondary = Color(0xFF0E2E28),
    secondaryContainer = Color(0xFF1F4A42),
    onSecondaryContainer = Color(0xFFD5E8E3),
    tertiary = Color(0xFFE0B04A),
    onTertiary = Color(0xFF2A1E00),
    background = Palette.Night,
    onBackground = Palette.NightInk,
    surface = Palette.NightCard,
    onSurface = Palette.NightInk,
    surfaceVariant = Color(0xFF35302A),
    onSurfaceVariant = Color(0xFFB9AC99),
    surfaceContainer = Palette.NightCard,
    surfaceContainerHigh = Color(0xFF302A24),
    surfaceContainerHighest = Color(0xFF38312A),
    surfaceContainerLow = Palette.NightCard,
    surfaceContainerLowest = Palette.Night,
    outline = Color(0xFF8F8373),
    outlineVariant = Color(0xFF4A4239),
    error = Color(0xFFE5675A),
)

private val LightGame = GameColors(
    ink = Palette.Ink,
    run = Palette.Terracotta,
    goal = Palette.Teal,
    success = Palette.Teal,
    warning = Color(0xFFB7791F),
    danger = Palette.Red,
    gold = Palette.Mustard,
    card = Palette.Paper,
    line = Palette.Line,
    track = Color(0xFFE9DFCC),
    muted = Palette.InkSoft,
)

private val DarkGame = GameColors(
    ink = Color(0xFF0E0C0A),
    run = Color(0xFFE5745A),
    goal = Color(0xFF6FB3A5),
    success = Color(0xFF6FB3A5),
    warning = Color(0xFFE0A84A),
    danger = Color(0xFFE5675A),
    gold = Color(0xFFE0B04A),
    card = Palette.NightCard,
    line = Color(0xFF4A4239),
    track = Color(0xFF3A332C),
    muted = Color(0xFFB9AC99),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

/** Big numbers on stat blocks. */
object NumberStyles {
    val huge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
    val big = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val medium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
}

val LocalGameColors = staticCompositionLocalOf { LightGame }
val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadii = staticCompositionLocalOf { Radii() }

object Gw {
    val colors: GameColors @Composable get() = LocalGameColors.current
    val space: Spacing @Composable get() = LocalSpacing.current
    val radii: Radii @Composable get() = LocalRadii.current
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun GoWaistTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val radii = Radii()
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalGameColors provides if (dark) DarkGame else LightGame,
        LocalSpacing provides Spacing(),
        LocalRadii provides radii,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(radii.s),
                medium = RoundedCornerShape(radii.m),
                large = RoundedCornerShape(radii.l),
                extraLarge = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}

fun Color.shade(factor: Float = 0.78f): Color = Color(red * factor, green * factor, blue * factor, alpha)
