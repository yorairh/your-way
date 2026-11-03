package com.yourway.app.data

import java.time.Duration
import java.time.LocalDateTime

class TripQuery(
    val from: Place,
    val to: Place,
    val mode: TimeMode,
    val time: LocalDateTime,
    val maxWalkMeters: Int,
    val hebrew: Boolean,
)

enum class TransitMode { BUS, TRAIN, LIGHT_RAIL }

sealed interface Leg {
    val start: LocalDateTime
    val end: LocalDateTime
}

class WalkLeg(
    override val start: LocalDateTime,
    override val end: LocalDateTime,
    val meters: Int,
    val from: LatLon,
    val to: LatLon,
    val toName: String,
) : Leg

class TransitLeg(
    override val start: LocalDateTime,
    override val end: LocalDateTime,
    val mode: TransitMode,
    val line: String,
    val headsign: String,
    val fromStop: String,
    val toStop: String,
    val stopCount: Int,
    /** The route drawn on the map, from where you board to where you get off. */
    val path: List<LatLon>,
    /** True only once the departure time includes a live delay. */
    val isLive: Boolean = false,
) : Leg

class Itinerary(val legs: List<Leg>) {
    val departure: LocalDateTime get() = legs.first().start
    val arrival: LocalDateTime get() = legs.last().end
    val duration: Duration get() = Duration.between(departure, arrival)
    val rides: List<TransitLeg> get() = legs.filterIsInstance<TransitLeg>()
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
    val walkMeters: Int get() = legs.filterIsInstance<WalkLeg>().sumOf { it.meters }
    val hasLiveData: Boolean get() = rides.any { it.isLive }
}

/** The schedule doesn't reach the date asked for. */
class ScheduleExpiredException : Exception()
