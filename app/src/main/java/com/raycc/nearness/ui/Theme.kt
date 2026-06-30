package com.raycc.nearness.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Warm = Color(0xFFE8A87C)
private val Sage = Color(0xFF85B79D)
private val Cream = Color(0xFFF3EDE3)
private val Ink = Color(0xFF2E2A26)

private val LightColors = lightColorScheme(
    primary = Warm,
    secondary = Sage,
    background = Cream,
    surface = Color(0xFFFBF8F2),
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Warm,
    secondary = Sage,
    background = Color(0xFF1B1916),
    surface = Color(0xFF252119),
)

@Composable
fun NearnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
