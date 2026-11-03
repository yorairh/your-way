package com.yourway.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yourway.app.data.AppContainer
import com.yourway.app.data.AppDatabase
import com.yourway.app.data.LatLon
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TripRepository
import com.yourway.app.data.WalkSetting
import com.yourway.app.ui.AppViewModel
import com.yourway.app.ui.YourWayScreens
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.robolectric.shadows.ShadowLooper

abstract class PickerTestBase {
    @get:Rule
    val compose = createComposeRule()

    protected val app: YourWayApp get() = ApplicationProvider.getApplicationContext()

    protected val azrieli = Place("Azrieli Center", "Tel Aviv", 32.07, 34.79, PlaceKind.ADDRESS)

    /** Shows the real screens with the real stop file, and stand-ins for network and location. */
    protected fun launch(
        loadStops: suspend () -> StopDirectory = { app.assets.open("stops.dat").use { StopDirectory.read(it) } },
        places: (String) -> List<Place> = { listOf(azrieli) },
        location: suspend () -> LatLon? = { LatLon(32.08, 34.78) },
        trips: TripRepository = TripRepository({ error("no schedule in this test") }, { error("no stops in this test") }),
    ): AppViewModel {
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        app.container = AppContainer(
            loadStops,
            { text, _ -> places(text) },
            { location() },
            database.savedPlaces(),
            WalkSetting(app),
            trips,
        )
        val viewModel = AppViewModel(app)
        compose.setContent { MaterialTheme { YourWayScreens(viewModel) } }
        return viewModel
    }

    protected fun waitForText(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        val matcher = hasText(text, substring = substring)
        while (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                val shown = compose.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { visibleText(it) }
                throw AssertionError("Waited for '$text' but the screen shows: $shown")
            }
            // Robolectric's main thread clock stands still unless a test moves it, and background work needs real time.
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            Thread.sleep(STEP_MILLIS)
            compose.waitForIdle()
        }
    }

    private fun visibleText(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap { visibleText(it) }

    private companion object {
        const val TIMEOUT = 15_000L
        const val STEP_MILLIS = 50L
    }
}
