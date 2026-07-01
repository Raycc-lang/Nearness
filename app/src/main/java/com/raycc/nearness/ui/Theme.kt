package com.raycc.nearness.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF607D6E),         // Muted sage green
    secondary = Color(0xFFB58A8A),       // Dusty rose
    background = Color(0xFFF9F6F0),      // Soft warm off-white / sand
    surface = Color(0xFFFFFFFF),         // Creamy white
    onBackground = Color(0xFF2C2C2E),    // Deep charcoal
    onSurface = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF7E7E82), // Muted warm gray
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7A9A8A),         // Muted sage green
    secondary = Color(0xFFD4A5A5),       // Dusty rose
    background = Color(0xFF121214),      // Dark charcoal slate
    surface = Color(0xFF1E1E20),         // Creamy dark surface
    onBackground = Color(0xFFE2E2E6),    // Light text
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFA1A1A5), // Muted warm gray
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
