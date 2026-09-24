package com.hoshina.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = HoshinaPrimary,
    secondary = HoshinaSecondary,
    background = HoshinaBackground,
    surface = HoshinaSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FD1E8),
    secondary = Color(0xFF9CCCDC),
    background = Color(0xFF101418),
    surface = Color(0xFF171C20),
    surfaceVariant = Color(0xFF263238),
    primaryContainer = Color(0xFF244D63),
    onPrimary = Color(0xFF102027),
    onSecondary = Color(0xFF102027),
    onBackground = Color(0xFFE8EEF2),
    onSurface = Color(0xFFE8EEF2),
)

@Composable
fun HoshinaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
