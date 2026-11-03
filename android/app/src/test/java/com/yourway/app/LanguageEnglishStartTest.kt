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
@Config(sdk = [34], qualifiers = "iw")
class LanguageEnglishStartTest : LanguageTestBase() {
    @Test
    fun savedEnglishChoiceWinsOnAHebrewPhone() {
        LanguageSetting.set(app, LanguageSetting.ENGLISH)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Choose a starting point")
            compose.onNodeWithText("Choose a starting point").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("place-search").performTextInput("38832")
            waitFor("Herzl/Bilu Junction")
        }
    }
}
