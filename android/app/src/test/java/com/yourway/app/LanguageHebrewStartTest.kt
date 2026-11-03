package com.yourway.app

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.yourway.app.data.LanguageSetting
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageHebrewStartTest : LanguageTestBase() {
    @Test
    fun savedHebrewChoiceTurnsTheWholeAppHebrewIncludingStopNames() {
        LanguageSetting.set(app, LanguageSetting.HEBREW)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("בחירת נקודת מוצא")
            compose.onNodeWithText("בחירת נקודת מוצא").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("place-search").performTextInput("38832")
            waitFor("הרצל/צומת בילו")
        }
    }
}
