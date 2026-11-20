package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val CerdasLightColorScheme = lightColorScheme(
    primary = CbtRoyalBlue,
    onPrimary = CbtCardWhite,
    primaryContainer = CbtBlueLightBg,
    onPrimaryContainer = CbtRoyalBlue,
    secondary = AiEmerald,
    onSecondary = CbtCardWhite,
    secondaryContainer = AiEmeraldLightBg,
    onSecondaryContainer = AiEmerald,
    background = CbtCanvasBg,
    onBackground = CbtTextPrimary,
    surface = CbtCardWhite,
    onSurface = CbtTextPrimary,
    surfaceVariant = CbtSubtleSurface,
    onSurfaceVariant = CbtTextSecondary,
    outline = CbtCardBorder,
    error = CbtDangerRed,
    onError = CbtCardWhite,
    errorContainer = CbtDangerLightBg,
    onErrorContainer = CbtDangerRed
)

val CerdasShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun CerdasCbtTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CerdasLightColorScheme,
        typography = CerdasCbtTypography,
        shapes = CerdasShapes,
        content = content
    )
}
