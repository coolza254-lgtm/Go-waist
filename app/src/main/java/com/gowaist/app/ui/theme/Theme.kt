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
 * Design tokens. Every colour, radius, spacing and text style used by the app comes from here,
 * so the whole look can be changed in one place.
 */

@Immutable
data class GameColors(
    val run: Color,
    val train: Color,
    val plan: Color,
    val goal: Color,
    val rest: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val gold: Color,
    val card: Color,
    val cardBorder: Color,
    val buttonShade: Color,
    val track: Color,
    val muted: Color,
    val heatLow: Color,
    val heatHigh: Color,
)

@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Minimum height of tappable controls (generous for sweaty hands). */
    val touch: Dp = 56.dp,
    val bigTouch: Dp = 72.dp,
)

@Immutable
data class Radii(
    val s: Dp = 12.dp,
    val m: Dp = 20.dp,
    val l: Dp = 28.dp,
    val pill: Dp = 100.dp,
)

object Palette {
    val Orange = Color(0xFFFB5B35)
    val OrangeDark = Color(0xFFC9401F)
    val Yellow = Color(0xFFFFD23F)
    val YellowDark = Color(0xFFD9A800)
    val Teal = Color(0xFF16C2A3)
    val TealDark = Color(0xFF0E8F78)
    val Blue = Color(0xFF3D8BFF)
    val BlueDark = Color(0xFF2361C4)
    val Purple = Color(0xFF8B5CF6)
    val PurpleDark = Color(0xFF6237C8)
    val Pink = Color(0xFFFF5C8A)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFE5484D)
    val Cream = Color(0xFFFFF7EC)
    val Ink = Color(0xFF2B2140)
    val Night = Color(0xFF1A1430)
    val NightCard = Color(0xFF2A2147)
}

private val LightScheme = lightColorScheme(
    primary = Palette.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D6),
    onPrimaryContainer = Color(0xFF5A1500),
    secondary = Palette.Yellow,
    onSecondary = Palette.Ink,
    secondaryContainer = Color(0xFFFFF0B8),
    onSecondaryContainer = Color(0xFF3D2E00),
    tertiary = Palette.Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8F5EA),
    onTertiaryContainer = Color(0xFF00382D),
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Color.White,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF6EBDD),
    onSurfaceVariant = Color(0xFF6B5E7A),
    surfaceContainer = Color(0xFFFFF1E2),
    surfaceContainerHigh = Color(0xFFFCEAD7),
    surfaceContainerLow = Color(0xFFFFF9F2),
    outline = Color(0xFFD9C7B4),
    outlineVariant = Color(0xFFEBDCCB),
    error = Palette.Red,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF7A57),
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF7A2A12),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Palette.Yellow,
    onSecondary = Palette.Ink,
    secondaryContainer = Color(0xFF5C4A00),
    onSecondaryContainer = Color(0xFFFFF0B8),
    tertiary = Color(0xFF3FE0C0),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF0D5446),
    onTertiaryContainer = Color(0xFFC8F5EA),
    background = Palette.Night,
    onBackground = Color(0xFFF4EEFF),
    surface = Palette.NightCard,
    onSurface = Color(0xFFF4EEFF),
    surfaceVariant = Color(0xFF3A3060),
    onSurfaceVariant = Color(0xFFC9BFE0),
    surfaceContainer = Color(0xFF241C40),
    surfaceContainerHigh = Color(0xFF30275A),
    surfaceContainerLow = Color(0xFF1F1838),
    outline = Color(0xFF5B4F80),
    outlineVariant = Color(0xFF40366A),
    error = Color(0xFFFF6B6F),
)

private val LightGame = GameColors(
    run = Palette.Orange,
    train = Palette.Blue,
    plan = Palette.Purple,
    goal = Palette.Teal,
    rest = Color(0xFF9AA5B8),
    success = Palette.Green,
    warning = Color(0xFFFFA41B),
    danger = Palette.Red,
    gold = Palette.Yellow,
    card = Color.White,
    cardBorder = Color(0xFFF0E1CF),
    buttonShade = Color(0x33000000),
    track = Color(0xFFF1E4D3),
    muted = Color(0xFF8E829E),
    heatLow = Color(0xFFFFE8C2),
    heatHigh = Palette.Orange,
)

private val DarkGame = GameColors(
    run = Color(0xFFFF7A57),
    train = Color(0xFF6AA6FF),
    plan = Color(0xFFA889FF),
    goal = Color(0xFF3FE0C0),
    rest = Color(0xFF7D86A0),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFFB547),
    danger = Color(0xFFFF6B6F),
    gold = Palette.Yellow,
    card = Palette.NightCard,
    cardBorder = Color(0xFF3B3166),
    buttonShade = Color(0x66000000),
    track = Color(0xFF3A3060),
    muted = Color(0xFFA79CC4),
    heatLow = Color(0xFF3A3060),
    heatHigh = Color(0xFFFF7A57),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Black),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Black),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp),
    )
}

/** Big numbers on stat tiles and timers. */
object NumberStyles {
    val huge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 68.sp)
    val big = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
    val medium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
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
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(radii.s),
                medium = RoundedCornerShape(radii.m),
                large = RoundedCornerShape(radii.l),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}

/** Slightly darker shade for the 3D bottom edge of game buttons. */
fun Color.shade(factor: Float = 0.78f): Color = Color(red * factor, green * factor, blue * factor, alpha)
