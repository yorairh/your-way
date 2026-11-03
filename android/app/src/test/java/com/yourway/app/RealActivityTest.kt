package com.yourway.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Starts the app the way a phone does, with nothing replaced, and shows a saved crash report first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class RealActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun waitFor(text: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "Never saw: $text" }
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            Thread.sleep(50)
            compose.waitForIdle()
        }
    }

    @Test
    fun crashReportThenRealStopSearchAndChoice() {
        val app: YourWayApp = ApplicationProvider.getApplicationContext()
        CrashReporter.save(app, "main", IllegalStateException("boom"))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("The app crashed last time")
            compose.onNodeWithText("Continue to the app").performClick()
            waitFor("Choose a starting point")
            assertNull(CrashReporter.pending(app))

            // This path, typing in the picker with the real stop file, is what crashed the first build.
            compose.onNodeWithText("Choose a starting point").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("place-search").performTextInput("38832")
            waitFor("Herzl/Bilu Junction")
            compose.onNodeWithText("Herzl/Bilu Junction").performClick()
            waitFor("Choose a destination")
            waitFor("Herzl/Bilu Junction")
        }
    }
}
