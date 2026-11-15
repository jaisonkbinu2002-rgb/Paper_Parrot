package com.genzcodeworks.paperparrot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ParrotPurple,
    onPrimary = Color.White,
    secondary = ParrotYellow,
    onSecondary = ParrotCharcoal,
    background = ParrotPurple,         // Top purple backdrop
    surface = LightSheetSurface,       // Rounded bottom sheet (#F7F7F7)
    onSurface = ParrotCharcoal,        // Primary text (#1A1A1A)
    surfaceVariant = LightIncomingBubble,
    onSurfaceVariant = LightSecondaryText
)

private val DarkColorScheme = darkColorScheme(
    primary = ParrotPurple,
    onPrimary = Color.White,
    secondary = ParrotYellow,
    onSecondary = ParrotCharcoal,
    background = DarkHeaderPurple,     // Deeper purple backdrop in dark mode
    surface = DarkSheetSurface,        // Dark bottom sheet (#121216)
    onSurface = Color(0xFFF7F7F7),     // Crisp off-white text
    surfaceVariant = DarkCardSurface,  // Incoming chat bubble in dark mode
    onSurfaceVariant = DarkSecondaryText
)

@Composable
fun PaperParrotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PaperParrotTypography,
        content = content
    )
}
