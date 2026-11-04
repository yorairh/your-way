package com.yourway.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yourway.app.data.AppTheme
import com.yourway.app.data.ThemeSetting
import com.yourway.app.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeChoiceTest : PickerTestBase() {
    @Test
    fun blueIsTheDefaultAndEachColourCanBeChosenAndIsRemembered() {
        assertEquals(AppTheme.BLUE, ThemeSetting.get(app))
        val model = launch()
        model.open(Screen.SETTINGS)
        waitForText("Colour")
        var shown = "Blue"
        for ((label, theme) in listOf("Green" to AppTheme.GREEN, "Orange" to AppTheme.ORANGE, "Purple" to AppTheme.PURPLE, "Blue" to AppTheme.BLUE)) {
            compose.onNodeWithText(shown).performClick() // opens the dropdown
            compose.waitForIdle()
            compose.onAllNodesWithText(label).onLast().performClick()
            shown = label
            compose.waitForIdle()
            assertEquals(theme, model.theme.value)
            assertEquals(theme, ThemeSetting.get(app))
        }
    }
}
