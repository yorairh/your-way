package com.yourway.app.routing

import com.yourway.app.data.Geo
import com.yourway.app.data.LatLon
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/** The bus, train and light rail schedule. The file is written by schedule/build_schedule.py. */
class Schedule(
    val stopCount: Int,
    val firstDate: Int,
    val lastDate: Int,
    val strings: List<String>,
    val routeShort: IntArray,
    val routeType: IntArray,
    val routeAgency: IntArray,
    val svcDays: IntArray,
    val svcStart: IntArray,
    val svcEnd: IntArray,
    val patRoute: IntArray,
    val patHeadsign: IntArray,
    val patShape: IntArray,
    val patStopStart: IntArray,
    val patStops: IntArray,
    val patArr: IntArray,
    val patDep: IntArray,
    val patFlags: IntArray,
    val patTripStart: IntArray,
    val tripStart: IntArray,
    val tripService: IntArray,
    val shapeStart: IntArray,
    shapeLatDeltas: IntArray,
    shapeLonDeltas: IntArray,
) {
    val patternCount: Int get() = patRoute.size

    /** For each stop, the patterns that call there and at which position along them. */
    val stopPatStart = IntArray(stopCount + 1)
    val stopPats: IntArray
    val stopPatPos: IntArray

    private val shapeLat = IntArray(shapeLatDeltas.size)
    private val shapeLon = IntArray(shapeLonDeltas.size)

    init {
        for (s in patStops) stopPatStart[s + 1]++
        for (i in 1..stopCount) stopPatStart[i] += stopPatStart[i - 1]
        stopPats = IntArray(patStops.size)
        stopPatPos = IntArray(patStops.size)
        val next = stopPatStart.copyOf()
        for (p in 0 until patternCount) {
            for (i in patStopStart[p] until patStopStart[p + 1]) {
                val at = next[patStops[i]]++
                stopPats[at] = p
                stopPatPos[at] = i - patStopStart[p]
            }
        }
        for (shape in 0 until shapeStart.size - 1) {
            var lat = 0
            var lon = 0
            for (i in shapeStart[shape] until shapeStart[shape + 1]) {
                lat += shapeLatDeltas[i]
                lon += shapeLonDeltas[i]
                shapeLat[i] = lat
                shapeLon[i] = lon
            }
        }
    }

    fun route(pattern: Int) = patRoute[pattern]
    fun line(pattern: Int) = strings[routeShort[patRoute[pattern]]]
    fun headsign(pattern: Int) = strings[patHeadsign[pattern]]
    fun routeType(pattern: Int) = routeType[patRoute[pattern]]

    /** Which services run on [date], indexed by service. */
    fun activeServices(date: LocalDate): BooleanArray {
        val day = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val bit = 1 shl (date.dayOfWeek.value % 7) // Bit 0 is Sunday, as in the file.
        return BooleanArray(svcDays.size) { svcStart[it] <= day && day <= svcEnd[it] && svcDays[it] and bit != 0 }
    }

    /**
     * The part of a pattern's route drawn between two of its stops. Follows the road shape when there is one,
     * otherwise runs straight from stop to stop.
     */
    fun path(pattern: Int, boardPos: Int, alightPos: Int, stopLat: (Int) -> Double, stopLon: (Int) -> Double): List<LatLon> {
        val base = patStopStart[pattern]
        val shape = patShape[pattern]
        val straight = (boardPos..alightPos).map { LatLon(stopLat(patStops[base + it]), stopLon(patStops[base + it])) }
        if (shape < 0) return straight
        val from = shapeStart[shape]
        val to = shapeStart[shape + 1]
        if (to - from < 2) return straight
        val start = nearestSegment(from, to, straight.first(), from)
        val end = nearestSegment(from, to, straight.last(), start.segment)
        val points = ArrayList<LatLon>()
        points += start.point
        for (i in start.segment + 1..end.segment) points += LatLon(shapeLat[i] / 1e6, shapeLon[i] / 1e6)
        points += end.point
        return points
    }

    private class Projection(val segment: Int, val point: LatLon)

    /** The closest point on the shape, searching segments from [searchFrom] on so that loops can't pull a stop backwards. */
    private fun nearestSegment(from: Int, to: Int, target: LatLon, searchFrom: Int): Projection {
        var bestDistance = Double.MAX_VALUE
        var best = Projection(searchFrom, target)
        for (i in maxOf(from, searchFrom) until to - 1) {
            val aLat = shapeLat[i] / 1e6
            val aLon = shapeLon[i] / 1e6
            val bLat = shapeLat[i + 1] / 1e6
            val bLon = shapeLon[i + 1] / 1e6
            val k = Math.cos(Math.toRadians(aLat))
            val dx = (bLon - aLon) * k
            val dy = bLat - aLat
            val length2 = dx * dx + dy * dy
            val t = if (length2 == 0.0) 0.0 else
                (((target.lon - aLon) * k * dx + (target.lat - aLat) * dy) / length2).coerceIn(0.0, 1.0)
            val lat = aLat + t * (bLat - aLat)
            val lon = aLon + t * (bLon - aLon)
            val d = Geo.straightMeters(target.lat, target.lon, lat, lon)
            if (d < bestDistance) {
                bestDistance = d
                best = Projection(i, LatLon(lat, lon))
            }
        }
        return best
    }

    companion object {
        fun read(input: InputStream): Schedule {
            val data = DataInputStream(GZIPInputStream(input, 1 shl 16).buffered(1 shl 16))
            val magic = ByteArray(4).also { data.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != "YWS1") throw IOException("Unknown schedule format")
            val stops = data.readInt()
            val first = data.readInt()
            val last = data.readInt()
            val blob = ByteArray(data.readInt()).also { data.readFully(it) }
            val strings = String(blob, Charsets.UTF_8).split('\n')
            fun ints(): IntArray {
                val bytes = ByteArray(data.readInt() * 4).also { data.readFully(it) }
                return IntArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).asIntBuffer().get(it) }
            }
            // Same order as the arrays are written in build_schedule.py.
            val routeShort = ints(); val routeType = ints(); val routeAgency = ints()
            val svcDays = ints(); val svcStart = ints(); val svcEnd = ints()
            val patRoute = ints(); val patHeadsign = ints(); val patShape = ints()
            val patStops = ints(); val patArr = ints(); val patDep = ints(); val patFlags = ints()
            val tripStart = ints(); val tripService = ints()
            val shapeLat = ints(); val shapeLon = ints()
            val patStopStart = ints(); val patTripStart = ints(); val shapeStart = ints()
            return Schedule(
                stops, first, last, strings, routeShort, routeType, routeAgency, svcDays, svcStart, svcEnd,
                patRoute, patHeadsign, patShape, patStopStart, patStops, patArr, patDep, patFlags, patTripStart,
                tripStart, tripService, shapeStart, shapeLat, shapeLon,
            )
        }
    }
}
