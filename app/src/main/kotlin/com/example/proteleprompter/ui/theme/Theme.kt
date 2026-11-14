package com.example.proteleprompter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = BroadcastCyan,
    onPrimary = Color(0xFF041E2B),
    primaryContainer = BroadcastCyanContainer,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = BroadcastAmber,
    onSecondary = Color(0xFF291800),
    secondaryContainer = BroadcastAmberContainer,
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = BroadcastPurple,
    background = StudioBlack,
    onBackground = Color(0xFFF1F5F9),
    surface = StudioSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = StudioCard,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioBorder,
    error = BroadcastRed
)

@Composable
fun ProTeleprompterTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
