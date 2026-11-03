package com.yourway.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.yourway.app.data.LanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageSwitchTest : LanguageTestBase() {
    @Test
    fun switchesToHebrewAndBackToEnglishAndRemembersTheChoice() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Choose a starting point")
            compose.onNode(hasContentDescription("Settings")).performClick()
            waitFor("Language")
            compose.onNodeWithText("עברית").performClick()
            waitFor("שפה")
            assertEquals(LanguageSetting.HEBREW, LanguageSetting.get(app))

            compose.onNodeWithText("English").performClick()
            waitFor("Language")
            assertEquals(LanguageSetting.ENGLISH, LanguageSetting.get(app))
        }
    }
}
