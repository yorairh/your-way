package com.yourway.app.routing

import com.yourway.app.data.Geo
import com.yourway.app.data.StopDirectory
import java.time.LocalDate

/** One stretch of a journey found by [Raptor]. Times are seconds from midnight of the search date. */
sealed interface RawLeg
class RawWalk(val fromStop: Int, val toStop: Int, val meters: Int) : RawLeg
class RawRide(val pattern: Int, val absoluteStart: Int, val boardPos: Int, val alightPos: Int) : RawLeg

class RawJourney(val legs: List<RawLeg>, val arrival: Int)

/**
 * Round-based transit search. Round k holds the earliest arrival at each stop using exactly k rides, so the
 * results trade arrival time against the number of transfers.
 */
class Raptor(private val schedule: Schedule, private val stops: StopDirectory) {

    fun search(
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
        date: LocalDate, departure: Int, maxWalkMeters: Int, excluded: BooleanArray? = null,
    ): List<RawJourney> {
        val active = arrayOf(
            schedule.activeServices(date.minusDays(1)), schedule.activeServices(date), schedule.activeServices(date.plusDays(1)),
        )
        val n = stops.count
        val arrival = Array(MAX_RIDES + 1) { IntArray(n) { NEVER } }
        val best = IntArray(n) { NEVER }
        val parentPattern = Array(MAX_RIDES + 1) { IntArray(n) }
        val parentStart = Array(MAX_RIDES + 1) { IntArray(n) }
        val parentBoard = Array(MAX_RIDES + 1) { IntArray(n) }
        val parentAlight = Array(MAX_RIDES + 1) { IntArray(n) }
        val accessMeters = IntArray(n) { -1 }
        val egressMeters = IntArray(n) { -1 }
        var marked = BooleanArray(n)

        stops.forEachNear(toLat, toLon, maxWalkMeters) { s, m -> egressMeters[s] = m }
        stops.forEachNear(fromLat, fromLon, maxWalkMeters) { s, m ->
            accessMeters[s] = m
            val t = departure + Geo.walkSeconds(m)
            arrival[0][s] = t
            best[s] = t
            marked[s] = true
        }

        val results = ArrayList<RawJourney>()
        var bestAtDestination = NEVER
        val firstPosition = IntArray(schedule.patternCount)

        for (round in 1..MAX_RIDES) {
            firstPosition.fill(-1)
            for (s in 0 until n) {
                if (!marked[s]) continue
                for (i in schedule.stopPatStart[s] until schedule.stopPatStart[s + 1]) {
                    val p = schedule.stopPats[i]
                    val pos = schedule.stopPatPos[i]
                    if (firstPosition[p] == -1 || pos < firstPosition[p]) firstPosition[p] = pos
                }
            }
            val nextMarked = BooleanArray(n)
            val previous = arrival[round - 1]
            val current = arrival[round]
            val slack = if (round == 1) 0 else TRANSFER_SLACK_SECONDS

            for (p in 0 until schedule.patternCount) {
                val from = firstPosition[p]
                if (from == -1 || excluded?.get(p) == true) continue
                val base = schedule.patStopStart[p]
                val length = schedule.patStopStart[p + 1] - base
                var tripStart = NEVER
                var board = -1
                for (pos in from until length) {
                    val s = schedule.patStops[base + pos]
                    val flags = schedule.patFlags[base + pos]
                    if (tripStart != NEVER && flags and FLAG_NO_DROP_OFF == 0) {
                        val t = tripStart + schedule.patArr[base + pos]
                        if (t < best[s] && t < bestAtDestination) {
                            current[s] = t
                            best[s] = t
                            parentPattern[round][s] = p
                            parentStart[round][s] = tripStart
                            parentBoard[round][s] = board
                            parentAlight[round][s] = pos
                            nextMarked[s] = true
                        }
                    }
                    val reached = previous[s]
                    if (reached != NEVER && flags and FLAG_NO_PICKUP == 0) {
                        val ready = reached + slack
                        if (tripStart == NEVER || ready <= tripStart + schedule.patDep[base + pos]) {
                            val found = earliestTrip(p, schedule.patDep[base + pos], ready, active)
                            if (found != NEVER && (tripStart == NEVER || found < tripStart)) {
                                tripStart = found
                                board = pos
                            }
                        }
                    }
                }
            }

            // Walk onward only from stops reached by a ride, so walks never chain past the user's limit.
            val reachedByRide = nextMarked.copyOf()
            for (s in 0 until n) {
                if (!reachedByRide[s]) continue
                val at = current[s]
                stops.forEachNear(stops.lat(s), stops.lon(s), maxWalkMeters) { other, m ->
                    val t = at + Geo.walkSeconds(m)
                    if (!reachedByRide[other] && t < best[other] && t < bestAtDestination) {
                        current[other] = t
                        best[other] = t
                        parentPattern[round][other] = WALKED
                        parentStart[round][other] = s
                        parentBoard[round][other] = m
                        nextMarked[other] = true
                    }
                }
            }

            var bestStop = -1
            for (s in 0 until n) {
                if (egressMeters[s] < 0 || current[s] == NEVER || !reachedByRide[s]) continue
                val t = current[s] + Geo.walkSeconds(egressMeters[s])
                if (t < bestAtDestination) {
                    bestAtDestination = t
                    bestStop = s
                }
            }
            if (bestStop != -1) {
                val legs = ArrayList<RawLeg>()
                legs += RawWalk(bestStop, DESTINATION, egressMeters[bestStop])
                var s = bestStop
                var r = round
                while (r > 0) {
                    if (parentPattern[r][s] == WALKED) {
                        legs += RawWalk(parentStart[r][s], s, parentBoard[r][s])
                        s = parentStart[r][s]
                    }
                    val p = parentPattern[r][s]
                    legs += RawRide(p, parentStart[r][s], parentBoard[r][s], parentAlight[r][s])
                    s = schedule.patStops[schedule.patStopStart[p] + parentBoard[r][s]]
                    r--
                }
                legs += RawWalk(ORIGIN, s, accessMeters[s])
                val forward = legs.asReversed().toList()
                results += RawJourney(nearerBoarding(forward, fromLat, fromLon, departure, maxWalkMeters), bestAtDestination)
            }

            if (nextMarked.none { it }) break
            marked = nextMarked
        }
        return results
    }

    /**
     * The search boards a trip at the first stop where it can be caught, which can be a long walk away from a stop
     * the same trip also serves, and arrives at exactly the same time. This moves each boarding to the stop of the
     * same trip with the least walking, as long as the passenger still gets there before the trip leaves it.
     */
    private fun nearerBoarding(legs: List<RawLeg>, fromLat: Double, fromLon: Double, departure: Int, maxWalk: Int): List<RawLeg> {
        val out = ArrayList<RawLeg>()
        var lat = fromLat
        var lon = fromLon
        var clock = departure
        var transfer = false
        var i = 0
        while (i < legs.size) {
            val leg = legs[i]
            val next = legs.getOrNull(i + 1)
            if (leg is RawWalk && next is RawRide) {
                val ride = withNearestBoarding(leg, next, lat, lon, clock, if (transfer) TRANSFER_SLACK_SECONDS else 0, maxWalk)
                out += ride.first
                out += ride.second
                clock = rideEnd(ride.second)
                lat = alightLat(ride.second)
                lon = alightLon(ride.second)
                transfer = true
                i += 2
            } else {
                if (leg is RawRide) {
                    clock = rideEnd(leg)
                    lat = alightLat(leg)
                    lon = alightLon(leg)
                    transfer = true
                }
                out += leg
                i++
            }
        }
        return out
    }

    private fun withNearestBoarding(
        walk: RawWalk, ride: RawRide, lat: Double, lon: Double, clock: Int, slack: Int, maxWalk: Int,
    ): Pair<RawWalk, RawRide> {
        val base = schedule.patStopStart[ride.pattern]
        var bestPos = ride.boardPos
        var bestMeters = walk.meters
        for (pos in 0 until ride.alightPos) {
            if (schedule.patFlags[base + pos] and FLAG_NO_PICKUP != 0) continue
            val stop = schedule.patStops[base + pos]
            val meters = Geo.walkMeters(lat, lon, stops.lat(stop), stops.lon(stop))
            if (meters > maxWalk) continue
            if (clock + Geo.walkSeconds(meters) + slack > ride.absoluteStart + schedule.patDep[base + pos]) continue
            if (meters < bestMeters || (meters == bestMeters && pos > bestPos)) {
                bestPos = pos
                bestMeters = meters
            }
        }
        if (bestPos == ride.boardPos) return walk to ride
        return RawWalk(walk.fromStop, schedule.patStops[base + bestPos], bestMeters) to
            RawRide(ride.pattern, ride.absoluteStart, bestPos, ride.alightPos)
    }

    private fun rideEnd(ride: RawRide) = ride.absoluteStart + schedule.patArr[schedule.patStopStart[ride.pattern] + ride.alightPos]
    private fun alightLat(ride: RawRide) = stops.lat(schedule.patStops[schedule.patStopStart[ride.pattern] + ride.alightPos])
    private fun alightLon(ride: RawRide) = stops.lon(schedule.patStops[schedule.patStopStart[ride.pattern] + ride.alightPos])

    /** Earliest absolute trip start whose departure from a stop [offset] seconds into the trip is at or after [ready]. */
    private fun earliestTrip(pattern: Int, offset: Int, ready: Int, active: Array<BooleanArray>): Int {
        val low = schedule.patTripStart[pattern]
        val high = schedule.patTripStart[pattern + 1]
        var best = NEVER
        for (day in 0..2) {
            val shift = (day - 1) * SECONDS_PER_DAY
            val target = ready - offset - shift
            var a = low
            var b = high
            while (a < b) {
                val mid = (a + b) ushr 1
                if (schedule.tripStart[mid] < target) a = mid + 1 else b = mid
            }
            for (i in a until high) {
                if (active[day][schedule.tripService[i]]) {
                    best = minOf(best, schedule.tripStart[i] + shift)
                    break
                }
            }
        }
        return best
    }

    companion object {
        const val ORIGIN = -1
        const val DESTINATION = -2
        const val MAX_RIDES = 5
        const val TRANSFER_SLACK_SECONDS = 120
        const val FLAG_NO_PICKUP = 1
        const val FLAG_NO_DROP_OFF = 2
        private const val NEVER = Int.MAX_VALUE
        private const val WALKED = -1
        private const val SECONDS_PER_DAY = 86_400
    }
}
