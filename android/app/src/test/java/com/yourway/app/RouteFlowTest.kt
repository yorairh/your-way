package com.yourway.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TripRepository
import com.yourway.app.ui.AppViewModel
import java.io.IOException
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** From choosing two places to the trip on screen, through the real search code on a small made-up network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouteFlowTest : PickerTestBase() {
    private val coordinates = listOf(32.0 to 34.8, 32.1 to 34.8, 32.2 to 34.8)
    private val stops = testStops(coordinates)
    private val everyTenMinutes = (0..170).map { it * 600 }
    private val schedule = testSchedule(
        3,
        listOf(
            TestPattern(
                "77", listOf(0, 1, 2), listOf(0, 600, 1200), everyTenMinutes,
                shape = listOf(31.99 to 34.8, 32.05 to 34.82, 32.1 to 34.8, 32.21 to 34.8),
            ),
        ),
    )
    private val oldDate = LocalDateTime.of(2020, 1, 1, 8, 0)
    private val wednesday = LocalDateTime.of(2026, 11, 4, 8, 0)

    private val here = Place("Start place", "Tel Aviv", 32.001, 34.8, PlaceKind.ADDRESS)
    private val there = Place("End place", "Haifa", 32.201, 34.8, PlaceKind.ADDRESS)

    private fun repository() = TripRepository({ schedule }, { stops })

    private fun searchBoth(model: AppViewModel, time: LocalDateTime = wednesday) {
        waitForText("Choose a starting point")
        compose.onNodeWithText("Choose a starting point").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("place-search").performTextInput("qqstart")
        waitForText("Start place")
        compose.onNodeWithText("Start place").performClick()
        waitForText("Choose a destination")
        compose.onNodeWithText("Choose a destination").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("place-search").performTextInput("qqend")
        waitForText("End place")
        compose.onNodeWithText("End place").performClick()
        waitForText("Find routes")
        model.setTimeMode(TimeMode.LEAVE_AT)
        model.setTime(time)
        compose.onNodeWithText("Find routes").performClick()
    }

    private val placeStub: (String) -> List<Place> = { text ->
        if (text.contains("start")) listOf(here) else listOf(there)
    }

    @Test
    fun twoPlacesToResultsToTripDetails() {
        val model = launch(places = placeStub, trips = repository())
        searchBoth(model)
        waitForText("Direct", substring = true)
        waitForText("77")
        compose.onAllNodes(hasText("77"))[0].performClick()
        waitForText("Line 77 to headsign")
        waitForText("Walk 144 m to stop0")
        // The real map needs native code that Robolectric lacks, so this also proves the fallback message appears.
        waitForText("The map couldn't be shown. The steps are listed below.")
        model.back()
        waitForText("Routes")
        model.back()
        waitForText("Find routes")
    }

    @Test
    fun aDateTheScheduleDoesNotCoverSaysSo() {
        val model = launch(places = placeStub, trips = repository())
        searchBoth(model, oldDate)
        waitForText("The schedule in this app doesn't cover that date. Try a nearer date.")
    }

    @Test
    fun noRouteFoundSaysSo() {
        val farAway = Place("Far place", "Eilat", 29.55, 34.95, PlaceKind.ADDRESS)
        val model = launch(places = { text -> if (text.contains("start")) listOf(here) else listOf(farAway.copy(name = "End place")) }, trips = repository())
        searchBoth(model)
        waitForText("No routes found. Try another time, or raise the longest walk in Settings.")
    }

    @Test
    fun aFailureWhileSearchingOffersToTryAgain() {
        val broken = TripRepository({ throw IOException("corrupt") }, { stops })
        val model = launch(places = placeStub, trips = broken)
        searchBoth(model)
        waitForText("Something went wrong finding routes.")
        waitForText("Try again")
    }

    @Test
    fun aScheduleFromADifferentDownloadThanTheStopsIsRefused() {
        val mismatched = TripRepository({ schedule }, { testStops(coordinates.take(2)) })
        val model = launch(places = placeStub, trips = mismatched)
        searchBoth(model)
        waitForText("Something went wrong finding routes.")
    }
}
