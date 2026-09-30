package com.prasbin.shadowmoney.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DarkPrimary = Color(0xFF05060C)
val DarkSurface = Color(0xFF0B0D17)
val DarkSurfaceVariant = Color(0xFF111524)
val DarkSurfaceElevated = Color(0xFF171C2E)
val DarkOnSurface = Color(0xFFE9EDF8)
val DarkOnSurfaceVariant = Color(0xFF8C96B0)
val NeonCyan = Color(0xFF3FD8FF)
val NeonPurple = Color(0xFF8B7BF0)
val NeonGreen = Color(0xFF33D9A0)
val AccentGold = Color(0xFFF2C94C)
val ErrorRed = Color(0xFFF0555E)
val WarningAmber = Color(0xFFF5A524)
val BorderColor = Color(0xFF232A40)
val CardColor = Color(0xFF0E1220)

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
    outlineVariant = Color(0xFF1A1F33),
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

val SystemShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun ShadowMoneyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = SystemShapes,
        content = content
    )
}
