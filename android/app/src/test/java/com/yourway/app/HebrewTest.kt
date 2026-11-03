package com.yourway.app

import android.view.View
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "iw")
class HebrewTest : PickerTestBase() {
    @Test
    fun screensAndStopsAreInHebrewAndRightToLeft() {
        launch()
        waitForText("בחירת נקודת מוצא")
        assertEquals(View.LAYOUT_DIRECTION_RTL, app.resources.configuration.layoutDirection)
        compose.onNodeWithText("בחירת נקודת מוצא").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("place-search").performTextInput("38832")
        waitForText("הרצל/צומת בילו")
        waitForText("קרית עקרון")
        compose.onNodeWithText("הרצל/צומת בילו").performClick()
        waitForText("בחירת יעד")
        waitForText("הרצל/צומת בילו")
    }

    @Test
    fun englishStopNamesAreSearchableFromHebrew() {
        launch()
        waitForText("בחירת נקודת מוצא")
        compose.onNodeWithText("בחירת נקודת מוצא").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("place-search").performTextInput("Herzl/Bilu Junction")
        waitForText("הרצל/צומת בילו")
    }
}
