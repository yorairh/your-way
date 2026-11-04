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
    /** How late the bus is running, when [isLive]; the times above already include it. */
    val delayMinutes: Int = 0,
    /** Which scheduled trip this is, so live data can be matched to it. */
    val ride: RideRef? = null,
) : Leg

/** A scheduled trip of a pattern: it starts at [startSeconds] after midnight of [date]. */
class RideRef(val pattern: Int, val startSeconds: Int, val boardPos: Int, val alightPos: Int, val date: java.time.LocalDate)

/**
 * One way of getting there. [alternatives] are the other lines that make the same journey between the same stops
 * (the first one is this itinerary itself), ordered by departure, so a single route can offer several buses.
 */
class Itinerary(val legs: List<Leg>, val missedTransfer: Boolean = false, val alternatives: List<Itinerary> = emptyList()) {
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
