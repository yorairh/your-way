package com.yourway.app.routing

import com.yourway.app.data.Geo
import com.yourway.app.data.Itinerary
import com.yourway.app.data.Leg
import com.yourway.app.data.RideRef
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.WalkLeg
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** The last known position of a bus on a particular trip. */
class VehicleFix(val lat: Double, val lon: Double, val recordedAt: Instant)

/** Where live bus positions come from. Returns null when there is no position for that trip. */
interface LiveSource {
    fun latestFix(lineRef: Int, scheduledStart: Instant): VehicleFix?
}

/**
 * Adds live delays to routes that were planned from the timetable. A bus's delay is how far behind the timetable it is at
 * its last reported position; it is assumed to stay the same until you board. Only buses that haven't yet reached
 * your boarding stop are adjusted, and anything the live source can't answer keeps its scheduled times.
 */
class LiveUpdater(
    private val schedule: Schedule,
    private val stops: StopDirectory,
    private val source: LiveSource,
    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem"),
    private val now: () -> Instant = Instant::now,
) {
    suspend fun apply(trips: List<Itinerary>): List<Itinerary> {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            coroutineScope {
                val rides = trips.flatMap { it.alternatives + it }.flatMap { it.rides }
                    .filter { it.mode == TransitMode.BUS }
                    .mapNotNull { it.ride }
                    .distinctBy { Triple(it.pattern, it.startSeconds, it.date) }
                val delays = rides.map { ref -> async(Dispatchers.IO) { ref to delayOf(ref) } }.awaitAll()
                    .associate { (ref, delay) -> Triple(ref.pattern, ref.startSeconds, ref.date) to delay }
                trips.map { adjust(it, delays) }
            }
        } ?: return trips
        // A route whose connection now fails goes last, otherwise the order stays as planned.
        return result.sortedBy { it.missedTransfer }
    }

    private fun delayOf(ref: RideRef): Int? = runCatching {
        if (schedule.routeId.isEmpty()) return@runCatching null
        val route = schedule.route(ref.pattern)
        val start = ZonedDateTime.of(ref.date, LocalTime.MIDNIGHT, zone).plusSeconds(ref.startSeconds.toLong()).toInstant()
        val fix = source.latestFix(schedule.routeId[route], start) ?: return@runCatching null
        estimateDelay(ref, start, fix)
    }.getOrNull()

    /** Seconds behind the timetable, or null when the report is too old or the bus is not before the boarding stop. */
    fun estimateDelay(ref: RideRef, start: Instant, fix: VehicleFix): Int? {
        if (abs(now().epochSecond - fix.recordedAt.epochSecond) > MAX_FIX_AGE_SECONDS) return null
        val base = schedule.patStopStart[ref.pattern]
        val count = schedule.patStopStart[ref.pattern + 1] - base
        if (count < 2) return null
        val scale = cos(Math.toRadians(fix.lat))
        var bestSegment = -1
        var bestFraction = 0.0
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until count - 1) {
            val a = schedule.patStops[base + i]
            val b = schedule.patStops[base + i + 1]
            val ax = stops.lon(a) * scale
            val ay = stops.lat(a)
            val dx = stops.lon(b) * scale - ax
            val dy = stops.lat(b) - ay
            val length2 = dx * dx + dy * dy
            val px = fix.lon * scale - ax
            val py = fix.lat - ay
            val f = if (length2 == 0.0) 0.0 else ((px * dx + py * dy) / length2).coerceIn(0.0, 1.0)
            val ex = px - f * dx
            val ey = py - f * dy
            val d = ex * ex + ey * ey
            if (d < bestDistance) {
                bestDistance = d
                bestSegment = i
                bestFraction = f
            }
        }
        if (bestSegment < 0) return null
        // Too far from the line to be this trip's bus.
        if (sqrt(bestDistance) * Geo.METERS_PER_DEGREE > MAX_OFF_ROUTE_METERS) return null
        if (bestSegment >= ref.boardPos) return null
        val from = schedule.patDep[base + bestSegment]
        val to = schedule.patArr[base + bestSegment + 1]
        val scheduled = from + bestFraction * (to - from)
        val elapsed = (fix.recordedAt.epochSecond - start.epochSecond).toDouble()
        val delay = (elapsed - scheduled).roundToInt()
        return if (delay < -MAX_EARLY_SECONDS || delay > MAX_LATE_SECONDS) null else delay.coerceAtLeast(0)
    }

    private fun adjust(trip: Itinerary, delays: Map<Triple<Int, Int, java.time.LocalDate>, Int?>): Itinerary {
        val options = if (trip.alternatives.isEmpty()) emptyList() else trip.alternatives.map { adjustOne(it, delays) }.sortedBy { it.departure }
        val self = adjustOne(trip, delays)
        return Itinerary(self.legs, self.missedTransfer, options)
    }

    private fun adjustOne(trip: Itinerary, delays: Map<Triple<Int, Int, java.time.LocalDate>, Int?>): Itinerary {
        fun delayOf(leg: TransitLeg): Int? = leg.ride?.let { delays[Triple(it.pattern, it.startSeconds, it.date)] }
        if (trip.rides.none { delayOf(it) != null }) return trip
        val legs = ArrayList<Leg>()
        var shift = 0L
        var missed = false
        for (leg in trip.legs) {
            when (leg) {
                is TransitLeg -> {
                    val delay = delayOf(leg)
                    if (delay != null) {
                        shift = delay.toLong()
                        legs += TransitLeg(
                            leg.start.plusSeconds(shift), leg.end.plusSeconds(shift), leg.mode, leg.line, leg.headsign,
                            leg.fromStop, leg.toStop, leg.stopCount, leg.path, true, (delay / 60.0).roundToInt(), leg.ride,
                        )
                    } else {
                        // This ride keeps its schedule; a delay on the ride before may break the connection to it.
                        if (shift > 0 && legs.isNotEmpty() && legs.last().end > leg.start) missed = true
                        shift = 0
                        legs += leg
                    }
                }
                is WalkLeg -> {
                    // A walk after a delayed ride starts later; the first walk, before any ride, leaves as planned.
                    legs += if (shift != 0L && legs.isNotEmpty()) {
                        WalkLeg(leg.start.plusSeconds(shift), leg.end.plusSeconds(shift), leg.meters, leg.from, leg.to, leg.toName)
                    } else {
                        leg
                    }
                }
            }
        }
        return Itinerary(legs, missed)
    }

    companion object {
        const val TIMEOUT_MS = 7_000L
        const val MAX_FIX_AGE_SECONDS = 900L
        const val MAX_OFF_ROUTE_METERS = 500.0
        const val MAX_EARLY_SECONDS = 300
        const val MAX_LATE_SECONDS = 5400
    }
}
