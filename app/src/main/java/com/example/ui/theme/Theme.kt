package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Navy900,
    onPrimary = Color.White,
    primaryContainer = Navy800,
    onPrimaryContainer = Color.White,
    secondary = Emerald600,
    onSecondary = Color.White,
    secondaryContainer = Emerald100,
    onSecondaryContainer = Emerald950,
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Amber900,
    error = Rose600,
    onError = Color.White,
    errorContainer = Rose100,
    onErrorContainer = Rose900,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    outlineVariant = Slate200,
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Navy900,
    primaryContainer = Navy800,
    onPrimaryContainer = Color.White,
    secondary = Emerald500,
    onSecondary = Navy900,
    secondaryContainer = Emerald950,
    onSecondaryContainer = Emerald100,
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber900,
    onTertiaryContainer = Amber100,
    error = Color(0xFFF87171),
    onError = Navy900,
    errorContainer = Rose900,
    onErrorContainer = Rose100,
    background = DarkBg,
    onBackground = Slate50,
    surface = DarkSurface,
    onSurface = Slate50,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Slate400,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun NominaProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
