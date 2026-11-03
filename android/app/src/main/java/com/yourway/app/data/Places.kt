package com.yourway.app.data

enum class PlaceKind { STOP, ADDRESS, CURRENT_LOCATION }

data class Place(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val kind: PlaceKind,
)

data class LatLon(val lat: Double, val lon: Double)

/** Addresses and named places. Implementations throw when the service can't be reached. */
fun interface PlaceSearch {
    fun search(text: String, hebrew: Boolean): List<Place>
}

/** Current position, or null when it can't be found. */
fun interface Locator {
    suspend fun current(): LatLon?
}

enum class TimeMode { NOW, LEAVE_AT, ARRIVE_BY }
