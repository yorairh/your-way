package com.yourway.app.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourway.app.R
import com.yourway.app.YourWayApp
import com.yourway.app.data.LanguageSetting
import com.yourway.app.data.Itinerary
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.SavedKind
import com.yourway.app.data.SavedPlace
import com.yourway.app.data.ScheduleExpiredException
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TripQuery
import com.yourway.app.data.WalkSetting
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SEARCH, PICKER, SAVED, SETTINGS, RESULTS, DETAIL }

/** What a chosen place will be used for. */
enum class PickFor { START, DESTINATION, HOME, WORK, FAVORITE }

data class PickerState(
    val query: String = "",
    val stops: List<Place> = emptyList(),
    val places: List<Place> = emptyList(),
    val searchingStops: Boolean = false,
    val searchingPlaces: Boolean = false,
    val stopsFailed: Boolean = false,
    val placesFailed: Boolean = false,
    val locating: Boolean = false,
    @StringRes val message: Int? = null,
)

sealed interface ResultsState {
    data object Loading : ResultsState
    data class Found(val trips: List<Itinerary>) : ResultsState
    data object Empty : ResultsState
    data object Expired : ResultsState
    data object Failed : ResultsState
}

data class UiState(
    val screen: Screen = Screen.SEARCH,
    val start: Place? = null,
    val destination: Place? = null,
    val timeMode: TimeMode = TimeMode.NOW,
    val time: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    val pickFor: PickFor = PickFor.START,
    val picker: PickerState = PickerState(),
    val results: ResultsState = ResultsState.Loading,
    val selected: Itinerary? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as YourWayApp).container
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val saved: StateFlow<List<SavedPlace>> = container.savedPlaces.observe()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val maxWalkMeters: StateFlow<Int> = container.walkSetting.meters
        .catch { emit(WalkSetting.DEFAULT_METERS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WalkSetting.DEFAULT_METERS)

    private var stopSearch: Job? = null
    private var placeSearch: Job? = null
    private var locating: Job? = null

    private val hebrew: Boolean
        get() = LanguageSetting.isHebrew(getApplication())

    fun open(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back() {
        cancelPickerWork()
        _state.update { current ->
            if (current.screen == Screen.DETAIL) return@update current.copy(screen = Screen.RESULTS)
            if (current.screen == Screen.RESULTS) return@update current.copy(screen = Screen.SEARCH)
            val cameFromSaved = current.screen == Screen.PICKER &&
                current.pickFor !in setOf(PickFor.START, PickFor.DESTINATION)
            current.copy(screen = if (cameFromSaved) Screen.SAVED else Screen.SEARCH)
        }
    }

    fun startPicking(pickFor: PickFor) {
        cancelPickerWork()
        _state.update { it.copy(screen = Screen.PICKER, pickFor = pickFor, picker = PickerState()) }
    }

    fun onQueryChange(text: String) {
        cancelPickerWork()
        val query = text.trim()
        _state.update {
            it.copy(
                picker = PickerState(
                    query = text,
                    searchingStops = query.isNotEmpty(),
                    searchingPlaces = query.length >= MIN_PLACE_QUERY,
                ),
            )
        }
        if (query.isEmpty()) return
        val useHebrew = hebrew
        stopSearch = viewModelScope.launch {
            val found = attempt { withContext(Dispatchers.Default) { container.stops().search(query, useHebrew, MAX_STOP_RESULTS) } }
            updatePicker { it.copy(stops = found.getOrDefault(emptyList()), searchingStops = false, stopsFailed = found.isFailure) }
        }
        if (query.length < MIN_PLACE_QUERY) return
        placeSearch = viewModelScope.launch {
            // Wait for a pause in typing, so the free place search isn't called on every letter.
            delay(TYPING_PAUSE_MILLIS)
            val found = attempt { withContext(Dispatchers.IO) { container.placeSearch.search(query, useHebrew) } }
            updatePicker { it.copy(places = found.getOrDefault(emptyList()), searchingPlaces = false, placesFailed = found.isFailure) }
        }
    }

    fun choose(place: Place) {
        cancelPickerWork()
        val pickFor = _state.value.pickFor
        when (pickFor) {
            PickFor.START, PickFor.DESTINATION -> {
                if (place.kind != PlaceKind.CURRENT_LOCATION) remember(SavedKind.RECENT, place)
                _state.update {
                    if (pickFor == PickFor.START) {
                        it.copy(start = place, screen = Screen.SEARCH)
                    } else {
                        it.copy(destination = place, screen = Screen.SEARCH)
                    }
                }
            }
            PickFor.HOME -> saveAndReturn(SavedKind.HOME, place)
            PickFor.WORK -> saveAndReturn(SavedKind.WORK, place)
            PickFor.FAVORITE -> saveAndReturn(SavedKind.FAVORITE, place)
        }
    }

    private fun saveAndReturn(kind: SavedKind, place: Place) {
        remember(kind, place)
        _state.update { it.copy(screen = Screen.SAVED) }
    }

    fun saveFavorite(place: Place) = remember(SavedKind.FAVORITE, place)

    fun deleteSaved(id: Long) {
        viewModelScope.launch { attempt { container.savedPlaces.delete(id) } }
    }

    private fun remember(kind: SavedKind, place: Place) {
        viewModelScope.launch {
            val result = attempt { container.savedPlaces.save(kind, place, System.currentTimeMillis()) }
            if (result.isFailure) updatePicker { it.copy(message = R.string.save_failed) }
        }
    }

    fun useCurrentLocation() {
        locating?.cancel()
        updatePicker { it.copy(locating = true, message = null) }
        locating = viewModelScope.launch {
            val fix = attempt { container.locator.current() }.getOrNull()
            if (fix == null) {
                updatePicker { it.copy(locating = false, message = R.string.location_unavailable) }
            } else {
                updatePicker { it.copy(locating = false) }
                choose(Place("", "", fix.lat, fix.lon, PlaceKind.CURRENT_LOCATION))
            }
        }
    }

    fun onLocationDenied() = updatePicker { it.copy(locating = false, message = R.string.location_denied) }

    private var routeSearch: Job? = null

    fun findRoutes() {
        val s = _state.value
        val start = s.start ?: return
        val destination = s.destination ?: return
        val time = if (s.timeMode == TimeMode.NOW) LocalDateTime.now().withSecond(0).withNano(0) else s.time
        routeSearch?.cancel()
        _state.update { it.copy(screen = Screen.RESULTS, results = ResultsState.Loading, selected = null) }
        routeSearch = viewModelScope.launch {
            val query = TripQuery(named(start), named(destination), s.timeMode, time, maxWalkMeters.value, hebrew)
            val result = attempt { withContext(Dispatchers.Default) { container.trips.plan(query) } }
            _state.update {
                it.copy(
                    results = result.fold(
                        onSuccess = { trips -> if (trips.isEmpty()) ResultsState.Empty else ResultsState.Found(trips) },
                        onFailure = { e -> if (e is ScheduleExpiredException) ResultsState.Expired else ResultsState.Failed },
                    ),
                )
            }
        }
    }

    fun select(trip: Itinerary) = _state.update { it.copy(selected = trip, screen = Screen.DETAIL) }

    /** A place's name in the app's language. The current location has no name of its own. */
    private fun named(place: Place): Place {
        if (place.kind != PlaceKind.CURRENT_LOCATION) return place
        val text = LanguageSetting.wrap(getApplication()).getString(R.string.my_location)
        return place.copy(name = text)
    }

    fun swap() = _state.update { it.copy(start = it.destination, destination = it.start) }

    fun setTimeMode(mode: TimeMode) = _state.update { it.copy(timeMode = mode) }

    fun setTime(time: LocalDateTime) = _state.update { it.copy(time = time) }

    fun setMaxWalk(meters: Int) {
        viewModelScope.launch { attempt { container.walkSetting.set(meters) } }
    }

    private fun updatePicker(change: (PickerState) -> PickerState) =
        _state.update { it.copy(picker = change(it.picker)) }

    private fun cancelPickerWork() {
        stopSearch?.cancel()
        placeSearch?.cancel()
        locating?.cancel()
    }

    /** Like runCatching, but cancellation still cancels. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val MIN_PLACE_QUERY = 2

        /** Few enough that address results, which come after them, are not pushed off the screen. */
        const val MAX_STOP_RESULTS = 5
        const val TYPING_PAUSE_MILLIS = 350L
    }
}
