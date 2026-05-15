package com.example.coin_nest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Mint200,
    onPrimary = Ink,
    secondary = Amber500,
    onSecondary = Ink,
    tertiary = Orange500,
    onTertiary = Snow,
    background = Color(0xFF101820),
    onBackground = Color(0xFFE8F0EF),
    surface = Color(0xFF18242D),
    onSurface = Color(0xFFE8F0EF),
    surfaceVariant = Color(0xFF253947),
    onSurfaceVariant = Color(0xFFC2CED2),
    outline = Color(0xFF586B76),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Teal700,
    onPrimary = Snow,
    secondary = Mint200,
    onSecondary = Navy800,
    tertiary = Amber500,
    onTertiary = Ink,
    background = Cloud50,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist100,
    onSurfaceVariant = Slate600,
    outline = Color(0xFFB8C5C2),
    error = Color(0xFFB3261E),
    onError = Color.White
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
