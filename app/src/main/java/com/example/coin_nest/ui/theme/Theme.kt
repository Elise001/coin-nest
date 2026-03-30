package com.example.coin_nest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Aqua300,
    onPrimary = Ink,
    secondary = Coral400,
    onSecondary = Ink,
    background = Navy900,
    onBackground = Snow,
    surface = Navy700,
    onSurface = Snow,
    surfaceVariant = Color(0xFF22324A),
    onSurfaceVariant = Color(0xFFB6C2D8)
)

private val LightColorScheme = lightColorScheme(
    primary = Aqua500,
    onPrimary = Snow,
    secondary = Coral400,
    onSecondary = Snow,
    background = Snow,
    onBackground = Ink,
    surface = Color(0xFFEAF0F8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFD8E2F0),
    onSurfaceVariant = Slate500
)

@Composable
fun CoinnestTheme(
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
