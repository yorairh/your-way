package com.yourway.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yourway.app.data.AppTheme

/** The main colour of each theme. Dark enough that white text on it is easy to read. */
fun themeSeed(theme: AppTheme): Color = when (theme) {
    AppTheme.BLUE -> Color(0xFF1565C0)
    AppTheme.GREEN -> Color(0xFF2E7D32)
    AppTheme.ORANGE -> Color(0xFFC9400B)
    AppTheme.PURPLE -> Color(0xFF6A1B9A)
}

/**
 * Material's default colour scheme is purple-tinted everywhere, so every colour that is shown, including the greys of
 * cards and backgrounds, is built here from the theme's own colour.
 */
fun yourWayColorScheme(theme: AppTheme, dark: Boolean): ColorScheme {
    val seed = themeSeed(theme)
    return if (dark) darkScheme(seed) else lightScheme(seed)
}

private fun lightScheme(seed: Color): ColorScheme {
    fun tint(amount: Float) = lerp(Color.White, seed, amount)
    val ink = Color(0xFF1B1B1F)
    return lightColorScheme(
        primary = seed, onPrimary = Color.White,
        primaryContainer = tint(0.22f), onPrimaryContainer = lerp(seed, Color.Black, 0.65f),
        secondary = lerp(seed, Color(0xFF5F6368), 0.55f), onSecondary = Color.White,
        secondaryContainer = tint(0.16f), onSecondaryContainer = lerp(seed, Color.Black, 0.7f),
        tertiary = lerp(seed, Color(0xFF5F6368), 0.55f), onTertiary = Color.White,
        tertiaryContainer = tint(0.16f), onTertiaryContainer = lerp(seed, Color.Black, 0.7f),
        background = tint(0.02f), onBackground = ink,
        surface = tint(0.02f), onSurface = ink,
        surfaceVariant = tint(0.12f), onSurfaceVariant = lerp(Color(0xFF444746), seed, 0.2f),
        outline = lerp(Color(0xFF747775), seed, 0.3f), outlineVariant = lerp(Color(0xFFCCCCCC), seed, 0.25f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = tint(0.04f), surfaceContainer = tint(0.07f),
        surfaceContainerHigh = tint(0.10f), surfaceContainerHighest = tint(0.13f),
    )
}

private fun darkScheme(seed: Color): ColorScheme {
    fun tint(base: Long, amount: Float) = lerp(Color(base), seed, amount)
    return darkColorScheme(
        primary = lerp(seed, Color.White, 0.55f), onPrimary = lerp(seed, Color.Black, 0.7f),
        primaryContainer = lerp(seed, Color.Black, 0.45f), onPrimaryContainer = lerp(seed, Color.White, 0.8f),
        secondary = lerp(seed, Color.White, 0.7f), onSecondary = lerp(seed, Color.Black, 0.75f),
        secondaryContainer = lerp(seed, Color.Black, 0.6f), onSecondaryContainer = lerp(seed, Color.White, 0.85f),
        tertiary = lerp(seed, Color.White, 0.7f), onTertiary = lerp(seed, Color.Black, 0.75f),
        tertiaryContainer = lerp(seed, Color.Black, 0.6f), onTertiaryContainer = lerp(seed, Color.White, 0.85f),
        background = tint(0xFF121212, 0.06f), onBackground = Color(0xFFE3E3E3),
        surface = tint(0xFF121212, 0.06f), onSurface = Color(0xFFE3E3E3),
        surfaceVariant = tint(0xFF1E1E1E, 0.15f), onSurfaceVariant = Color(0xFFC4C7C5),
        outline = Color(0xFF8E918F), outlineVariant = tint(0xFF444444, 0.2f),
        surfaceContainerLowest = tint(0xFF0E0E0E, 0.05f), surfaceContainerLow = tint(0xFF1A1A1A, 0.07f),
        surfaceContainer = tint(0xFF1E1E1E, 0.09f), surfaceContainerHigh = tint(0xFF252525, 0.11f),
        surfaceContainerHighest = tint(0xFF2C2C2C, 0.13f),
    )
}
