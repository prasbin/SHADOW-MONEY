package com.prasbin.shadowmoney.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkPrimary = Color(0xFF0A0A1A)
val DarkSurface = Color(0xFF12121F)
val DarkSurfaceVariant = Color(0xFF1A1A2E)
val DarkSurfaceElevated = Color(0xFF22223A)
val DarkOnSurface = Color(0xFFE0E0E8)
val DarkOnSurfaceVariant = Color(0xFF8888AA)
val NeonCyan = Color(0xFF00D4FF)
val NeonPurple = Color(0xFF7B68EE)
val NeonGreen = Color(0xFF00FF88)
val AccentGold = Color(0xFFFFD700)
val ErrorRed = Color(0xFFFF4444)
val WarningAmber = Color(0xFFFFAA00)
val BorderColor = Color(0xFF333355)
val CardColor = Color(0xFF1E1E30)

val LightPrimary = Color(0xFF0A0A1A)
val LightSurface = Color(0xFFF5F5F5)
val LightSurfaceVariant = Color(0xFFE0E0E0)
val LightOnSurface = Color(0xFF1A1A1A)
val LightOnSurfaceVariant = Color(0xFF555555)

val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkPrimary,
    secondary = NeonPurple,
    onSecondary = DarkSurface,
    tertiary = NeonGreen,
    onTertiary = DarkPrimary,
    background = DarkPrimary,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
    onError = DarkOnSurface,
    outline = BorderColor,
    outlineVariant = Color(0xFF2A2A44),
)

val LightColorScheme = lightColorScheme(
    primary = NeonCyan,
    onPrimary = LightPrimary,
    secondary = NeonPurple,
    onSecondary = LightSurface,
    tertiary = NeonGreen,
    onTertiary = LightPrimary,
    background = LightSurface,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
    onError = LightOnSurface,
    outline = BorderColor,
    outlineVariant = Color(0xFFBBBBCC),
)

@Composable
fun ShadowMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
