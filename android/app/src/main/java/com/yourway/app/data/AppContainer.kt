package com.yourway.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yourway.app.BuildConfig
import com.yourway.app.routing.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.settingsStore by preferencesDataStore("settings")

class WalkSetting(private val context: Context) {
    private val key = intPreferencesKey("max_walk_meters")
    val meters: Flow<Int> = context.settingsStore.data.map { it[key] ?: DEFAULT_METERS }

    suspend fun set(value: Int) {
        context.settingsStore.edit { it[key] = value }
    }

    companion object {
        const val DEFAULT_METERS = 1000
    }
}

/** What the screens depend on, in one place. */
class AppContainer(
    private val loadStops: suspend () -> StopDirectory,
    val placeSearch: PlaceSearch,
    val locator: Locator,
    val savedPlaces: SavedPlaceDao,
    val walkSetting: WalkSetting,
    val trips: TripRepository,
    val scheduleUpdater: ScheduleUpdater? = null,
) {
    private val lock = Mutex()
    private var stops: StopDirectory? = null

    /** Loads on first use. A failed load throws, and the next call tries again. */
    suspend fun stops(): StopDirectory = lock.withLock {
        stops ?: loadStops().also { stops = it }
    }

    companion object {
        /** Loads once, on first use. A failed load throws, and the next call tries again. */
        private fun <T : Any> cached(load: suspend () -> T): suspend () -> T {
            val lock = Mutex()
            var value: T? = null
            return { lock.withLock { value ?: load().also { value = it } } }
        }

        fun create(context: Context): AppContainer {
            // The files are gzipped but must not be named .gz: the build unpacks those and drops the extension.
            // The two files belong together, so they come from the same place: the last good download, else the app itself.
            val updater = ScheduleUpdater.create(context, BuildConfig.VERSION_CODE)
            val data = cached {
                withContext(Dispatchers.IO) {
                    updater.loadDownloaded() ?: ScheduleData(
                        context.assets.open("schedule.dat").use { Schedule.read(it) },
                        context.assets.open("stops.dat").use { StopDirectory.read(it) },
                        null,
                    )
                }
            }
            val stops: suspend () -> StopDirectory = { data().stops }
            val schedule: suspend () -> Schedule = { data().schedule }
            return AppContainer(
                scheduleUpdater = updater,
                loadStops = stops,
                placeSearch = PhotonSearch(),
                locator = AndroidLocator(context),
                savedPlaces = AppDatabase.create(context).savedPlaces(),
                walkSetting = WalkSetting(context),
                trips = TripRepository(schedule, stops, StrideLiveSource()),
            )
        }
    }
}
