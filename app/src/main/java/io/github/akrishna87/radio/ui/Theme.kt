package io.github.akrishna87.radio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Deep blue-black with a teal accent; follows the phone's dark/light setting. */
private val Dark = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0F4D47),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF0B0F3A),
    secondaryContainer = Color(0xFF262B55),
    onSecondaryContainer = Color(0xFFE0E3FF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1A2129),
    onSurfaceVariant = Color(0xFF9AA7B4),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = Color(0xFF151B22),
    surfaceContainerHigh = Color(0xFF1C232B),
    surfaceContainerHighest = Color(0xFF242C35),
    outline = Color(0xFF3A4652),
    outlineVariant = Color(0xFF26303A),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5EA),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF0B0F3A),
    background = Color(0xFFF7FAFB),
    onBackground = Color(0xFF12181E),
    surface = Color(0xFFF7FAFB),
    onSurface = Color(0xFF12181E),
    surfaceVariant = Color(0xFFE3EAEE),
    onSurfaceVariant = Color(0xFF51606C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4F6),
    surfaceContainer = Color(0xFFEAF0F2),
    surfaceContainerHigh = Color(0xFFE3EAED),
    surfaceContainerHighest = Color(0xFFDCE4E8),
    outline = Color(0xFF8A99A4),
    outlineVariant = Color(0xFFCBD5DB),
    error = Color(0xFFB3261E),
)

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
