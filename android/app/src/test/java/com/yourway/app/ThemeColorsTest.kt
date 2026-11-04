package com.yourway.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yourway.app.data.AppTheme
import com.yourway.app.ui.yourWayColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun textIsReadableOnItsBackgroundInEveryThemeAndMode() {
        for (theme in AppTheme.values()) {
            for (dark in listOf(false, true)) {
                val c = yourWayColorScheme(theme, dark)
                val pairs = mapOf(
                    "primary" to (c.onPrimary to c.primary),
                    "primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
                    "secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
                    "surface" to (c.onSurface to c.surface),
                    "surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
                    "card" to (c.onSurface to c.surfaceContainerHighest),
                    "background" to (c.onBackground to c.background),
                )
                for ((name, colors) in pairs) {
                    val ratio = contrast(colors.first, colors.second)
                    assertTrue("$theme dark=$dark $name contrast is only %.2f".format(ratio), ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun everyThemeLooksDifferent() {
        val primaries = AppTheme.values().map { yourWayColorScheme(it, false).primary }
        assertTrue(primaries.toSet().size == AppTheme.values().size)
    }
}
