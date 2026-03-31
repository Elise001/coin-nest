package com.example.coin_nest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Orange500,
    onPrimary = Snow,
    secondary = Amber500,
    onSecondary = Snow,
    background = Color(0xFF2D1C10),
    onBackground = Color(0xFFFFE9D0),
    surface = Color(0xFF3A2515),
    onSurface = Color(0xFFFFE9D0),
    surfaceVariant = Color(0xFF5C3A24),
    onSurfaceVariant = Color(0xFFE7C9A8)
)

private val LightColorScheme = lightColorScheme(
    primary = Orange500,
    onPrimary = Snow,
    secondary = Amber500,
    onSecondary = Snow,
    background = Cream50,
    onBackground = Ink,
    surface = Cream100,
    onSurface = Ink,
    surfaceVariant = Sand200,
    onSurfaceVariant = Brown500
)

@Composable
fun CoinnestTheme(
    darkTheme: Boolean = false,
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
