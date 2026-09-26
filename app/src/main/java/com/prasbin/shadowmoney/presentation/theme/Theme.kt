package com.prasbin.shadowmoney.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPrimary = Color(0xFF0A0A1A)
private val DarkSurface = Color(0xFF12121F)
private val DarkSurfaceVariant = Color(0xFF1A1A2E)
private val DarkSurfaceElevated = Color(0xFF22223A)
private val DarkOnSurface = Color(0xFFE0E0E8)
private val DarkOnSurfaceVariant = Color(0xFF8888AA)
private val NeonCyan = Color(0xFF00D4FF)
private val NeonPurple = Color(0xFF7B68EE)
private val NeonGreen = Color(0xFF00FF88)
private val AccentGold = Color(0xFFFFD700)
private val ErrorRed = Color(0xFFFF4444)
private val WarningAmber = Color(0xFFFFAA00)
private val BorderColor = Color(0xFF333355)
private val CardColor = Color(0xFF1E1E30)

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
    surfaceElevated = DarkSurfaceElevated,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
    onError = DarkOnSurface,
    outline = BorderColor,
    outlineVariant = Color(0xFF2A2A44),
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
