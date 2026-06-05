package com.caminerin.backingtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared corporate palette inherited from the sibling app "Guitar Trainer"
 * (amber / dark Material 3) so both apps keep one visual identity.
 * Dynamic color is intentionally disabled to preserve that identity.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4960A),
    secondary = Color(0xFFE67E00),
    tertiary = Color(0xFF8BC34A),
    background = Color(0xFF0F0D0A),
    surface = Color(0xFF1A1714),
    onPrimary = Color(0xFF0F0D0A),
    onSecondary = Color(0xFF0F0D0A),
    onBackground = Color(0xFFF0E8D8),
    onSurface = Color(0xFFF0E8D8),
    error = Color(0xFFD84315),
    onError = Color.White,
    surfaceVariant = Color(0xFF201C16),
    onSurfaceVariant = Color(0xFF8B7D6B),
    outline = Color(0xFF2A2420),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD4960A),
    secondary = Color(0xFFE67E00),
    tertiary = Color(0xFF8BC34A),
    background = Color(0xFFF8F0E0),
    surface = Color(0xFFFFF8F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1714),
    onSurface = Color(0xFF1A1714),
)

@Composable
fun BackingTrackTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
