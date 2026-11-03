package com.yourway.app

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.yourway.app.data.StopDirectory
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class PickerSearchTest : PickerTestBase() {
    private fun openStartPicker() {
        waitForText("Choose a starting point")
        compose.onNodeWithText("Choose a starting point").performClick()
        compose.waitForIdle()
    }

    private fun type(text: String) = compose.onNodeWithTag("place-search").performTextInput(text)

    @Test
    fun choosingARealStopReturnsToSearchWithItsName() {
        launch()
        openStartPicker()
        type("Herzl/Bilu Junction")
        waitForText("Kiryat Ekron")
        compose.onNodeWithText("Kiryat Ekron").performClick()
        waitForText("Herzl/Bilu Junction")
        waitForText("Choose a destination")
    }

    @Test
    fun findsAStopByNumber() {
        launch()
        openStartPicker()
        type("38832")
        waitForText("Kiryat Ekron")
    }

    @Test
    fun choosingAnAddressWorksAndAppearsInRecents() {
        launch()
        openStartPicker()
        type(NO_STOP_MATCHES)
        waitForText("Azrieli Center")
        compose.onNodeWithText("Azrieli Center").performClick()
        waitForText("Choose a destination")
        compose.onNodeWithText("Choose a destination").performClick()
        waitForText("Azrieli Center")
    }

    @Test
    fun addressSearchFailureShowsMessageAndStopsStillWork() {
        launch(places = { throw IOException("no internet") })
        openStartPicker()
        type(NO_STOP_MATCHES)
        waitForText("Address search isn't available right now. Stops still work.")
        compose.onNodeWithTag("place-search").performTextClearance()
        type("38832")
        waitForText("Kiryat Ekron")
    }

    @Test
    fun stopLoadFailureShowsMessageInsteadOfCrashing() {
        launch(loadStops = { throw IOException("bad file") }, places = { emptyList() })
        openStartPicker()
        type("Herzl")
        waitForText("Couldn't load stops. Close the app and try again.")
    }

    @Test
    fun brokenStopFileShowsMessageInsteadOfCrashing() {
        launch(loadStops = { StopDirectory.read("not a gzip file".byteInputStream()) }, places = { emptyList() })
        openStartPicker()
        type("Herzl")
        waitForText("Couldn't load stops. Close the app and try again.")
    }

    @Test
    fun nothingFoundSaysSo() {
        launch(places = { emptyList() })
        openStartPicker()
        type("zzzzzzqqq")
        waitForText("Nothing found. Try different words.")
    }

    @Test
    fun leavingWhileSearchingDoesNotCrash() {
        val viewModel = launch(places = { Thread.sleep(300); listOf(azrieli) })
        openStartPicker()
        type("Her")
        type("zl")
        viewModel.back()
        waitForText("Choose a starting point")
        Thread.sleep(800)
        compose.waitForIdle()
    }

    private companion object {
        const val NO_STOP_MATCHES = "qqqqq"
    }
}
