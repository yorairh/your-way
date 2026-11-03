package com.yourway.app.data

import java.io.InputStream
import kotlin.math.cos
import kotlin.math.floor
import java.util.zip.GZIPInputStream

/** All stops, searchable in Hebrew and English. The file is written by schedule/build_stops.py. */
class StopDirectory private constructor(private val stops: List<Stop>) {
    val count: Int get() = stops.size

    private val grid = HashMap<Long, IntArray>().also { cells ->
        val members = HashMap<Long, MutableList<Int>>()
        stops.forEachIndexed { i, s -> members.getOrPut(cellKey(cellOf(s.lat), cellOf(s.lon))) { ArrayList() }.add(i) }
        members.forEach { (key, list) -> cells[key] = list.toIntArray() }
    }

    fun lat(stop: Int) = stops[stop].lat
    fun lon(stop: Int) = stops[stop].lon
    fun code(stop: Int) = stops[stop].code
    fun name(stop: Int, hebrew: Boolean) = if (hebrew) stops[stop].nameHe else stops[stop].nameEn.ifEmpty { stops[stop].nameHe }

    /** Calls [action] with each stop within [meters] (by estimated walking distance) of a point. */
    fun forEachNear(lat: Double, lon: Double, meters: Int, action: (stop: Int, meters: Int) -> Unit) {
        val reach = meters / Geo.WALK_FACTOR
        val rows = (reach / Geo.METERS_PER_DEGREE / CELL_DEGREES).toInt() + 1
        val cols = (reach / (Geo.METERS_PER_DEGREE * cos(Math.toRadians(lat))) / CELL_DEGREES).toInt() + 1
        val row0 = cellOf(lat)
        val col0 = cellOf(lon)
        for (row in row0 - rows..row0 + rows) {
            for (col in col0 - cols..col0 + cols) {
                val members = grid[cellKey(row, col)] ?: continue
                for (i in members) {
                    val d = Geo.walkMeters(lat, lon, stops[i].lat, stops[i].lon)
                    if (d <= meters) action(i, d)
                }
            }
        }
    }

    private class Stop(
        val code: String,
        val lat: Double,
        val lon: Double,
        val nameHe: String,
        val nameEn: String,
        val cityHe: String,
        val cityEn: String,
    ) {
        val searchEn = nameEn.lowercase()
    }

    fun search(text: String, hebrew: Boolean, limit: Int = 12): List<Place> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        if (query.all { it.isDigit() }) {
            return stops.filter { it.code == query }.take(limit).map { it.toPlace(hebrew) }
        }
        val lower = query.lowercase()
        val first = ArrayList<Stop>()
        val rest = ArrayList<Stop>()
        for (stop in stops) {
            when {
                stop.nameHe.startsWith(query) || stop.searchEn.startsWith(lower) -> first += stop
                stop.nameHe.contains(query) || stop.searchEn.contains(lower) -> rest += stop
            }
        }
        // Opposite sides of a street share a name, so one result per name and city is enough to choose from.
        return (first + rest).asSequence()
            .map { it.toPlace(hebrew) }
            .distinctBy { it.name to it.subtitle }
            .take(limit)
            .toList()
    }

    private fun Stop.toPlace(hebrew: Boolean): Place {
        val name = if (hebrew) nameHe else nameEn.ifEmpty { nameHe }
        val city = if (hebrew) cityHe else cityEn.ifEmpty { cityHe }
        return Place(name, city, lat, lon, PlaceKind.STOP)
    }

    companion object {
        private const val CELL_DEGREES = 0.01

        private fun cellOf(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

        private fun cellKey(row: Int, col: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xffffffffL)

        fun read(input: InputStream): StopDirectory {
            val stops = ArrayList<Stop>(32_000)
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val f = line.split('\t')
                if (f.size == 7) stops += Stop(f[0], f[1].toDouble(), f[2].toDouble(), f[3], f[4], f[5], f[6])
            }
            return StopDirectory(stops)
        }
    }
}
