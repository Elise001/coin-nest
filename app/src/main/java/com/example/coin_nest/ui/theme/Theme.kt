package com.example.coin_nest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Frost200,
    onPrimary = Ink,
    secondary = Peach300,
    onSecondary = Ink,
    tertiary = Mint500,
    onTertiary = Snow,
    background = Color(0xFF111827),
    onBackground = Color(0xFFF8FBFF),
    surface = Color(0xFF182033),
    onSurface = Color(0xFFF8FBFF),
    surfaceVariant = Color(0xFF263149),
    onSurfaceVariant = Color(0xFFD8DFEA),
    outline = Color(0xFF6F7C91),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Sky700,
    onPrimary = Snow,
    secondary = Peach300,
    onSecondary = Ink,
    tertiary = Mint500,
    onTertiary = Snow,
    background = Cloud50,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist100,
    onSurfaceVariant = Slate600,
    outline = Color(0xFFCBD5E1),
    error = Coral500,
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
