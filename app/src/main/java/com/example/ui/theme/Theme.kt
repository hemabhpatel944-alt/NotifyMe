package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class NotifymeSemanticColors(
    val isDark: Boolean,
    val subtleBorder: Color,
    val codeSurface: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val info: Color,
    val infoBg: Color,
    val neutral: Color,
    val neutralBg: Color,
    val cyanAccent: Color,
)

private val DarkSemanticColors = NotifymeSemanticColors(
    isDark = true,
    subtleBorder = ObsidianBorder,
    codeSurface = Color(0xFF0D111A),
    success = StatusEmerald,
    successBg = StatusEmeraldBgDark,
    warning = StatusAmber,
    warningBg = StatusAmberBgDark,
    danger = StatusRose,
    dangerBg = StatusRoseBgDark,
    info = StatusSky,
    infoBg = StatusSkyBgDark,
    neutral = StatusSlate,
    neutralBg = StatusSlateBgDark,
    cyanAccent = CyanAccent,
)

private val LightSemanticColors = NotifymeSemanticColors(
    isDark = false,
    subtleBorder = AlabasterBorder,
    codeSurface = Color(0xFFF1F5F9),
    success = Color(0xFF059669),
    successBg = StatusEmeraldBgLight,
    warning = Color(0xFFD97706),
    warningBg = StatusAmberBgLight,
    danger = Color(0xFFE11D48),
    dangerBg = StatusRoseBgLight,
    info = Color(0xFF0284C7),
    infoBg = StatusSkyBgLight,
    neutral = Color(0xFF64748B),
    neutralBg = StatusSlateBgLight,
    cyanAccent = CyanLightAccent,
)

val LocalNotifymeColors = staticCompositionLocalOf { DarkSemanticColors }

private val NotifymeDarkColorScheme = darkColorScheme(
    primary = IndigoAccent,
    onPrimary = Color.White,
    primaryContainer = IndigoDim,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = CyanAccent,
    onSecondary = Color(0xFF042F3A),
    secondaryContainer = CyanDim,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = StatusEmerald,
    onTertiary = Color(0xFF022C22),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF242F47),
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderSubtle,
    error = StatusRose,
    onError = Color.White,
    errorContainer = StatusRoseBgDark,
    onErrorContainer = Color(0xFFFFD9E0),
)

private val NotifymeLightColorScheme = lightColorScheme(
    primary = IndigoLightPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoLightContainer,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = CyanLightAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = AlabasterSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = AlabasterSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = AlabasterSurfaceElevated,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = AlabasterBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = StatusRoseBgLight,
    onErrorContainer = Color(0xFF881337),
)

val NotifymeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun NotifymeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NotifymeDarkColorScheme else NotifymeLightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalNotifymeColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NotifymeTypography,
            shapes = NotifymeShapes,
            content = content
        )
    }
}
