package com.yourway.app

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.robolectric.shadows.ShadowLooper

/**
 * Starts the real activity, as a phone does. Android keeps one view-model factory, and so one app object,
 * for a whole test run, so each class here holds a single test that depends on the saved language.
 */
abstract class LanguageTestBase {
    @get:Rule
    val compose = createEmptyComposeRule()

    protected val app: YourWayApp get() = ApplicationProvider.getApplicationContext()

    protected fun waitFor(text: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                val shown = compose.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { visibleText(it) }
                throw AssertionError("Never saw '$text'. The screen shows: $shown")
            }
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            Thread.sleep(50)
            compose.waitForIdle()
        }
    }

    private fun visibleText(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap { visibleText(it) }
}
