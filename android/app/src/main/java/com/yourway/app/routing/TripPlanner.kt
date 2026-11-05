package com.yourway.app.routing

import com.yourway.app.data.Geo
import com.yourway.app.data.Itinerary
import com.yourway.app.data.RideRef
import com.yourway.app.data.LatLon
import com.yourway.app.data.Leg
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.TripQuery
import com.yourway.app.data.WalkLeg
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Turns searches into the top routes: early arrival first, with less walking and fewer transfers winning when times are close. */
class TripPlanner(private val schedule: Schedule, private val stops: StopDirectory) {
    private val raptor = Raptor(schedule, stops)

    fun plan(query: TripQuery): List<Itinerary> =
        if (query.mode == TimeMode.ARRIVE_BY) planArriveBy(query) else planDepartAt(query)

    private fun planDepartAt(query: TripQuery): List<Itinerary> {
        val found = ArrayList<Itinerary>()
        directWalk(query, query.time)?.let { found += it }
        val excluded = BooleanArray(schedule.patternCount)
        for (attempt in 0 until MAX_SEARCHES) {
            // The first search also tries shorter walks, in case a nearby stop is only a little slower.
            val limits = if (attempt == 0) walkLimits(query.maxWalkMeters) else listOf(query.maxWalkMeters)
            val batch = limits.flatMap { search(query, query.time, excluded, it) }
            if (batch.isEmpty()) break
            found += batch.map { it.second }
            if (groups(found) >= RESULT_COUNT && distinct(found) >= MIN_OPTIONS) break
            batch.forEach { exclude(it.first, excluded) }
        }
        return rank(found, byDeparture = false).map { withOtherLines(it, query) }
    }

    private fun planArriveBy(query: TripQuery): List<Itinerary> {
        val found = ArrayList<Itinerary>()
        directWalk(query, query.time)?.let { walk ->
            found += directWalk(query, query.time.minus(walk.duration))!!
        }
        val excluded = BooleanArray(schedule.patternCount)
        var searches = 0
        var step = 1L
        var triedShorterWalks = false
        while (step <= MAX_ARRIVE_BY_STEPS && searches < MAX_ARRIVE_BY_SEARCHES && distinct(found) < RESULT_COUNT) {
            val departAt = query.time.minusMinutes(step * ARRIVE_BY_STEP_MINUTES)
            val limits = if (triedShorterWalks) listOf(query.maxWalkMeters) else walkLimits(query.maxWalkMeters)
            val batch = limits.flatMap { search(query, departAt, excluded, it) }.filter { !it.second.arrival.isAfter(query.time) }
            searches++
            if (batch.isEmpty()) {
                step++
            } else {
                triedShorterWalks = true
                found += batch.map { it.second }
                batch.forEach { exclude(it.first, excluded) }
            }
        }
        return rank(found, byDeparture = true)
    }

    private fun walkLimits(max: Int) = listOf(max, max / 2, max / 4).filter { it == max || it >= MIN_SHORTER_WALK }.distinct()

    private fun search(query: TripQuery, at: LocalDateTime, excluded: BooleanArray, walkLimit: Int): List<Pair<RawJourney, Itinerary>> {
        val date = at.toLocalDate()
        return raptor.search(
            query.from.lat, query.from.lon, query.to.lat, query.to.lon,
            date, at.toLocalTime().toSecondOfDay(), walkLimit, excluded,
        ).map { it to toItinerary(it, date, query) }
    }

    private fun directWalk(query: TripQuery, at: LocalDateTime): Itinerary? {
        val meters = Geo.walkMeters(query.from.lat, query.from.lon, query.to.lat, query.to.lon)
        if (meters > query.maxWalkMeters) return null
        val end = at.plusSeconds(Geo.walkSeconds(meters).toLong())
        return Itinerary(listOf(WalkLeg(at, end, meters, LatLon(query.from.lat, query.from.lon), LatLon(query.to.lat, query.to.lon), query.to.name)))
    }

    /**
     * For a route that is a single ride, finds the other lines that go between the same two stops, by searching again
     * with every other line set aside, one line at a time, so the route can offer several buses.
     */
    private fun withOtherLines(trip: Itinerary, query: TripQuery): Itinerary {
        val ref = trip.rides.singleOrNull()?.ride ?: return trip
        if (query.mode == TimeMode.ARRIVE_BY) return trip
        val base = schedule.patStopStart[ref.pattern]
        val board = schedule.patStops[base + ref.boardPos]
        val alight = schedule.patStops[base + ref.alightPos]
        val excluded = BooleanArray(schedule.patternCount) { true }
        for (k in schedule.stopPatStart[board] until schedule.stopPatStart[board + 1]) {
            val pattern = schedule.stopPats[k]
            val from = schedule.stopPatPos[k]
            val start = schedule.patStopStart[pattern]
            val end = schedule.patStopStart[pattern + 1]
            if ((from + 1 until end - start).any { schedule.patStops[start + it] == alight }) excluded[pattern] = false
        }
        val key = trip.journeyKey()
        val others = ArrayList<Itinerary>()
        for (attempt in 0 until MAX_OPTIONS + 1) {
            val batch = search(query, query.time, excluded, query.maxWalkMeters)
            if (batch.isEmpty()) break
            others += batch.map { it.second }.filter { it.journeyKey() == key }
            batch.forEach { exclude(it.first, excluded) }
        }
        // A bus an hour later is not an option worth listing beside the next ones.
        val all = (trip.alternatives + others).distinctBy { it.signature() }.sortedBy { it.departure }
        val options = all.filter { it === trip || !it.departure.isAfter(all.first().departure.plusMinutes(OPTION_WINDOW_MINUTES)) }.take(MAX_OPTIONS)
        return Itinerary(trip.legs, trip.missedTransfer, options.map { withLaterRuns(it, query) })
    }

    /** The next few runs of the same line between the same stops, found by searching after this one leaves. */
    private fun withLaterRuns(trip: Itinerary, query: TripQuery): Itinerary {
        val ref = trip.rides.singleOrNull()?.ride ?: return trip
        val base = schedule.patStopStart[ref.pattern]
        val board = schedule.patStops[base + ref.boardPos]
        val alight = schedule.patStops[base + ref.alightPos]
        val excluded = BooleanArray(schedule.patternCount) { true }
        for (k in schedule.stopPatStart[board] until schedule.stopPatStart[board + 1]) {
            val pattern = schedule.stopPats[k]
            if (schedule.routeShort[schedule.route(pattern)] != schedule.routeShort[schedule.route(ref.pattern)]) continue
            val start = schedule.patStopStart[pattern]
            val end = schedule.patStopStart[pattern + 1]
            if ((schedule.stopPatPos[k] + 1 until end - start).any { schedule.patStops[start + it] == alight }) excluded[pattern] = false
        }
        val key = trip.journeyKey()
        val later = ArrayList<Itinerary>()
        var after = trip
        for (i in 0 until LATER_RUNS) {
            // Leaving a minute after the last one may still catch the same bus (a walk comes first), so step on until a later one shows.
            val boarded = after.rides.first().start
            var at = after.departure.plusMinutes(1)
            var found: Itinerary? = null
            while (found == null && !at.isAfter(boarded.plusMinutes(1))) {
                val batch = search(query, at, excluded, query.maxWalkMeters).map { it.second }.filter { it.journeyKey() == key }
                if (batch.isEmpty()) break
                found = batch.filter { it.rides.first().start.isAfter(boarded) }.minByOrNull { it.departure }
                at = at.plusMinutes(1)
            }
            // The search rolls over to later days; a run that far away is not "the next bus".
            if (found == null || found.departure.isAfter(trip.departure.plusMinutes(LATER_WINDOW_MINUTES))) break
            later += found
            after = found
        }
        return Itinerary(trip.legs, trip.missedTransfer, trip.alternatives, later)
    }

    private fun distinct(list: List<Itinerary>) = list.distinctBy { it.signature() }.size
    private fun groups(list: List<Itinerary>) = list.distinctBy { it.journeyKey() }.size

    /** Journeys that board and leave at the same stops are the same route, whatever line they use. */
    private fun Itinerary.journeyKey(): String {
        if (rides.isEmpty()) return "walk"
        return rides.joinToString("|") {
            val ref = it.ride ?: return@joinToString it.line
            val base = schedule.patStopStart[ref.pattern]
            "${schedule.patStops[base + ref.boardPos]}-${schedule.patStops[base + ref.alightPos]}"
        }
    }

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

    /**
     * Best first. A route costs its arrival time (or, for arrive by, how early you must leave), plus half its walking
     * time again and five minutes per transfer. So a route that is a minute or two slower but walks much less, or
     * changes less, comes first. Routes that use the same lines are the same choice, and only the best is kept.
     */
    private fun rank(list: List<Itinerary>, byDeparture: Boolean): List<Itinerary> {
        fun cost(trip: Itinerary): Double {
            val time = if (byDeparture) -trip.departure.toEpochSecond(ZoneOffset.UTC) else trip.arrival.toEpochSecond(ZoneOffset.UTC)
            return time + WALK_WEIGHT * Geo.walkSeconds(trip.walkMeters) + TRANSFER_PENALTY_SECONDS * trip.transfers
        }
        val best = list.sortedBy(::cost).distinctBy { it.signature() }
        // Several lines over the same stops become one route offering a few buses.
        return best.groupBy { it.journeyKey() }.values.take(RESULT_COUNT).map { members ->
            val options = members.sortedBy { it.departure }.take(MAX_OPTIONS)
            val primary = members.first()
            Itinerary(primary.legs, primary.missedTransfer, if (options.contains(primary)) options else (options.dropLast(1) + primary).sortedBy { it.departure })
        }
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
                        ride = RideRef(leg.pattern, leg.absoluteStart, leg.boardPos, leg.alightPos, date),
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

    private fun Itinerary.signature() = rides.joinToString("|") { "${it.mode}:${it.line}:${it.headsign}" }

    private companion object {
        const val RESULT_COUNT = 3
        const val MIN_OPTIONS = 6
        const val MAX_OPTIONS = 5
        const val LATER_RUNS = 2
        const val LATER_WINDOW_MINUTES = 90L
        const val OPTION_WINDOW_MINUTES = 40L
        const val MAX_SEARCHES = 8
        const val MAX_ARRIVE_BY_STEPS = 16
        const val MAX_ARRIVE_BY_SEARCHES = 20
        const val ARRIVE_BY_STEP_MINUTES = 15L
        const val ROUTE_TYPE_TRAIN = 2
        const val ROUTE_TYPE_LIGHT_RAIL = 0
        const val MIN_SHORTER_WALK = 150
        const val WALK_WEIGHT = 0.5
        const val TRANSFER_PENALTY_SECONDS = 300
    }
}
