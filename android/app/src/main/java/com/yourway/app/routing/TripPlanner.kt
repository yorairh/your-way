package com.yourway.app.routing

import com.yourway.app.data.Geo
import com.yourway.app.data.Itinerary
import com.yourway.app.data.LatLon
import com.yourway.app.data.Leg
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.TripQuery
import com.yourway.app.data.WalkLeg
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Turns searches into the top routes: earliest arrival, with fewer transfers winning when times are close. */
class TripPlanner(private val schedule: Schedule, private val stops: StopDirectory) {
    private val raptor = Raptor(schedule, stops)

    fun plan(query: TripQuery): List<Itinerary> =
        if (query.mode == TimeMode.ARRIVE_BY) planArriveBy(query) else planDepartAt(query)

    private fun planDepartAt(query: TripQuery): List<Itinerary> {
        val found = ArrayList<Itinerary>()
        directWalk(query, query.time)?.let { found += it }
        val excluded = BooleanArray(schedule.patternCount)
        repeat(MAX_SEARCHES) {
            val batch = search(query, query.time, excluded)
            if (batch.isEmpty()) return rankByArrival(found)
            found += batch.map { it.second }
            if (distinct(found) >= RESULT_COUNT) return rankByArrival(found)
            batch.forEach { exclude(it.first, excluded) }
        }
        return rankByArrival(found)
    }

    private fun planArriveBy(query: TripQuery): List<Itinerary> {
        val found = ArrayList<Itinerary>()
        directWalk(query, query.time)?.let { walk ->
            found += directWalk(query, query.time.minus(walk.duration))!!
        }
        val excluded = BooleanArray(schedule.patternCount)
        var searches = 0
        var step = 1L
        while (step <= MAX_ARRIVE_BY_STEPS && searches < MAX_ARRIVE_BY_SEARCHES && distinct(found) < RESULT_COUNT) {
            val departAt = query.time.minusMinutes(step * ARRIVE_BY_STEP_MINUTES)
            val batch = search(query, departAt, excluded).filter { !it.second.arrival.isAfter(query.time) }
            searches++
            if (batch.isEmpty()) {
                step++
            } else {
                found += batch.map { it.second }
                batch.forEach { exclude(it.first, excluded) }
            }
        }
        return rankByDeparture(found)
    }

    private fun search(query: TripQuery, at: LocalDateTime, excluded: BooleanArray): List<Pair<RawJourney, Itinerary>> {
        val date = at.toLocalDate()
        return raptor.search(
            query.from.lat, query.from.lon, query.to.lat, query.to.lon,
            date, at.toLocalTime().toSecondOfDay(), query.maxWalkMeters, excluded,
        ).map { it to toItinerary(it, date, query) }
    }

    private fun directWalk(query: TripQuery, at: LocalDateTime): Itinerary? {
        val meters = Geo.walkMeters(query.from.lat, query.from.lon, query.to.lat, query.to.lon)
        if (meters > query.maxWalkMeters) return null
        val end = at.plusSeconds(Geo.walkSeconds(meters).toLong())
        return Itinerary(listOf(WalkLeg(at, end, meters, LatLon(query.from.lat, query.from.lon), LatLon(query.to.lat, query.to.lon), query.to.name)))
    }

    private fun distinct(list: List<Itinerary>) = list.distinctBy { it.signature() }.size

    /**
     * A later run of the same line would otherwise beat every real alternative, so a found journey's longest ride
     * is set aside before searching again. Trains have no line names, so only trains serving both stops are.
     */
    private fun exclude(raw: RawJourney, excluded: BooleanArray) {
        val ride = raw.legs.filterIsInstance<RawRide>().maxByOrNull {
            val base = schedule.patStopStart[it.pattern]
            schedule.patArr[base + it.alightPos] - schedule.patDep[base + it.boardPos]
        } ?: return
        val type = schedule.routeType(ride.pattern)
        val base = schedule.patStopStart[ride.pattern]
        val board = schedule.patStops[base + ride.boardPos]
        val alight = schedule.patStops[base + ride.alightPos]
        for (p in 0 until schedule.patternCount) {
            if (schedule.routeType(p) != type) continue
            excluded[p] = excluded[p] || if (type == ROUTE_TYPE_TRAIN) {
                val range = schedule.patStopStart[p] until schedule.patStopStart[p + 1]
                range.any { schedule.patStops[it] == board } && range.any { schedule.patStops[it] == alight }
            } else {
                schedule.routeShort[schedule.route(p)] == schedule.routeShort[schedule.route(ride.pattern)]
            }
        }
    }

    private fun rankByArrival(list: List<Itinerary>): List<Itinerary> {
        val unique = list.sortedBy { it.arrival }.distinctBy { it.signature() }
        return preferFewerTransfers(unique) { a, b -> Duration.between(a.arrival, b.arrival) }
    }

    private fun rankByDeparture(list: List<Itinerary>): List<Itinerary> {
        val unique = list.sortedByDescending { it.departure }.distinctBy { it.signature() }
        return preferFewerTransfers(unique) { a, b -> Duration.between(b.departure, a.departure) }
    }

    private fun preferFewerTransfers(sorted: List<Itinerary>, gap: (Itinerary, Itinerary) -> Duration): List<Itinerary> {
        val out = sorted.toMutableList()
        for (pass in out.indices) {
            for (i in 0 until out.size - 1) {
                val a = out[i]
                val b = out[i + 1]
                if (b.transfers < a.transfers && gap(a, b) <= CLOSE_TIMES) {
                    out[i] = b
                    out[i + 1] = a
                }
            }
        }
        return out.take(RESULT_COUNT)
    }

    private fun toItinerary(raw: RawJourney, date: LocalDate, query: TripQuery): Itinerary {
        val midnight = date.atStartOfDay()
        fun time(seconds: Int) = midnight.plusSeconds(seconds.toLong())
        fun point(stop: Int) = when (stop) {
            Raptor.ORIGIN -> LatLon(query.from.lat, query.from.lon)
            Raptor.DESTINATION -> LatLon(query.to.lat, query.to.lon)
            else -> LatLon(stops.lat(stop), stops.lon(stop))
        }
        val legs = ArrayList<Leg>()
        var clock = 0
        raw.legs.forEachIndexed { index, leg ->
            when (leg) {
                is RawRide -> {
                    val base = schedule.patStopStart[leg.pattern]
                    val start = leg.absoluteStart + schedule.patDep[base + leg.boardPos]
                    val end = leg.absoluteStart + schedule.patArr[base + leg.alightPos]
                    legs += TransitLeg(
                        time(start), time(end), modeOf(schedule.routeType(leg.pattern)), lineName(leg.pattern, query.hebrew),
                        schedule.headsign(leg.pattern),
                        stops.name(schedule.patStops[base + leg.boardPos], query.hebrew),
                        stops.name(schedule.patStops[base + leg.alightPos], query.hebrew),
                        leg.alightPos - leg.boardPos,
                        schedule.path(leg.pattern, leg.boardPos, leg.alightPos, stops::lat, stops::lon),
                    )
                    clock = end
                }
                is RawWalk -> {
                    val seconds = Geo.walkSeconds(leg.meters)
                    val toName = if (leg.toStop == Raptor.DESTINATION) query.to.name else stops.name(leg.toStop, query.hebrew)
                    if (index == 0) {
                        // Leave just in time for the first ride rather than waiting at the stop.
                        val ride = raw.legs[1] as RawRide
                        val board = ride.absoluteStart + schedule.patDep[schedule.patStopStart[ride.pattern] + ride.boardPos]
                        if (leg.meters > 0) {
                            legs += WalkLeg(time(board - seconds), time(board), leg.meters, point(leg.fromStop), point(leg.toStop), toName)
                        }
                    } else {
                        if (leg.meters > 0) {
                            legs += WalkLeg(time(clock), time(clock + seconds), leg.meters, point(leg.fromStop), point(leg.toStop), toName)
                        }
                        clock += seconds
                    }
                }
            }
        }
        return Itinerary(legs)
    }

    private fun lineName(pattern: Int, hebrew: Boolean): String {
        val short = schedule.line(pattern)
        return when {
            short.isNotBlank() -> short
            schedule.routeType(pattern) == ROUTE_TYPE_TRAIN -> if (hebrew) "רכבת" else "Train"
            else -> ""
        }
    }

    private fun modeOf(routeType: Int) = when (routeType) {
        ROUTE_TYPE_TRAIN -> TransitMode.TRAIN
        ROUTE_TYPE_LIGHT_RAIL -> TransitMode.LIGHT_RAIL
        else -> TransitMode.BUS
    }

    private fun Itinerary.signature() =
        rides.joinToString("|") { "${it.mode}:${it.line}:${it.fromStop}:${it.toStop}" }

    private companion object {
        const val RESULT_COUNT = 3
        const val MAX_SEARCHES = 8
        const val MAX_ARRIVE_BY_STEPS = 16
        const val MAX_ARRIVE_BY_SEARCHES = 20
        const val ARRIVE_BY_STEP_MINUTES = 15L
        const val ROUTE_TYPE_TRAIN = 2
        const val ROUTE_TYPE_LIGHT_RAIL = 0
        val CLOSE_TIMES: Duration = Duration.ofMinutes(5)
    }
}
