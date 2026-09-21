package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkProfessionalColorScheme = darkColorScheme(
  primary = AccentPrimary,
  onPrimary = OnAccentPrimary,
  primaryContainer = AccentPrimaryContainer,
  onPrimaryContainer = Color(0xFFDBEAFE),
  secondary = Color(0xFF64748B),
  onSecondary = Color.White,
  tertiary = Color(0xFF94A3B8),
  onTertiary = Color(0xFF0F172A),
  background = DarkBackground,
  onBackground = TextPrimary,
  surface = DarkSurface,
  onSurface = TextPrimary,
  surfaceVariant = DarkSurfaceElevated,
  onSurfaceVariant = TextSecondary,
  outline = BorderStrong,
  outlineVariant = BorderSubtle,
  error = StatusError,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkProfessionalColorScheme,
    typography = Typography,
    content = content
  )
}
