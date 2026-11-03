package com.yourway.app

import android.Manifest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class PickerLocationAndSavedTest : PickerTestBase() {
    private fun openStartPicker() {
        waitForText("Choose a starting point")
        compose.onNodeWithText("Choose a starting point").performClick()
        compose.waitForIdle()
    }

    private fun grantLocation() = Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    @Test
    fun currentLocationIsUsedWhenAvailable() {
        grantLocation()
        launch()
        openStartPicker()
        compose.onNodeWithText("My location").performClick()
        waitForText("Choose a destination")
        waitForText("My location")
    }

    @Test
    fun missingLocationShowsMessage() {
        grantLocation()
        launch(location = { null })
        openStartPicker()
        compose.onNodeWithText("My location").performClick()
        waitForText("Couldn't find your location. Check that location is on.")
    }

    @Test
    fun locationErrorShowsMessage() {
        grantLocation()
        launch(location = { throw SecurityException("revoked") })
        openStartPicker()
        compose.onNodeWithText("My location").performClick()
        waitForText("Couldn't find your location. Check that location is on.")
    }

    @Test
    fun deniedPermissionShowsMessage() {
        val viewModel = launch()
        openStartPicker()
        compose.onNodeWithText("My location").performClick()
        compose.waitForIdle()
        // Robolectric can't press the system dialog's button, so this delivers its answer directly.
        viewModel.onLocationDenied()
        waitForText("Location permission wasn't given.")
    }

    @Test
    fun homeCanBeSetFromSavedPlaces() {
        launch()
        waitForText("Choose a starting point")
        compose.onNode(hasContentDescription("Saved places")).performClick()
        waitForText("Not set")
        compose.onNode(hasText("Home")).performClick()
        waitForText("Stop, stop number, address or place")
        compose.onNodeWithTag("place-search").performTextInput("qqqqq")
        waitForText("Azrieli Center")
        compose.onNodeWithText("Azrieli Center").performClick()
        waitForText("Azrieli Center")
        waitForText("Saved places")
    }
}
