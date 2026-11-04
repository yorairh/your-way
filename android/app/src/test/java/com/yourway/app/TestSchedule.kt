package com.yourway.app

import com.yourway.app.data.StopDirectory
import com.yourway.app.routing.Schedule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** One bus line in a made-up network. */
class TestPattern(
    val line: String,
    val stops: List<Int>,
    val offsets: List<Int>,
    val starts: List<Int>,
    val service: Int = 0,
    val flags: List<Int> = stops.map { 0 },
    val routeType: Int = 3,
    val shape: List<Pair<Double, Double>>? = null,
)

fun testStops(coordinates: List<Pair<Double, Double>>): StopDirectory {
    val text = coordinates.mapIndexed { i, (lat, lon) -> "${1000 + i}\t$lat\t$lon\tעצירה$i\tstop$i\tעיר\tcity" }.joinToString("\n")
    val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    return StopDirectory.read(ByteArrayInputStream(bytes))
}

/** Service 0 runs every day and service 1 only on Sundays, both through 2026. */
fun testSchedule(stopCount: Int, patterns: List<TestPattern>): Schedule {
    val strings = ArrayList<String>().apply { add(""); add("headsign") }
    fun str(s: String) = strings.indexOf(s).takeIf { it >= 0 } ?: strings.apply { add(s) }.lastIndex
    val lines = patterns.map { str(it.line) }
    val shapes = patterns.mapNotNull { it.shape }
    var shapeNumber = 0
    val patShape = patterns.map { if (it.shape == null) -1 else shapeNumber++ }
    val shapeStart = shapes.runningFold(0) { a, s -> a + s.size }
    fun deltas(pick: (Pair<Double, Double>) -> Double) = shapes.flatMap { shape ->
        var previous = 0
        shape.map { p -> val e6 = (pick(p) * 1e6).toInt(); (e6 - previous).also { previous = e6 } }
    }.toIntArray()
    return Schedule(
        stopCount, 20260101, 20261231, strings,
        lines.toIntArray(), patterns.map { it.routeType }.toIntArray(), IntArray(patterns.size),
        intArrayOf(127, 1), intArrayOf(20260101, 20260101), intArrayOf(20261231, 20261231),
        IntArray(patterns.size) { it }, IntArray(patterns.size) { 1 }, patShape.toIntArray(),
        patterns.runningFold(0) { a, p -> a + p.stops.size }.toIntArray(), patterns.flatMap { it.stops }.toIntArray(),
        patterns.flatMap { it.offsets }.toIntArray(), patterns.flatMap { it.offsets }.toIntArray(),
        patterns.flatMap { it.flags }.toIntArray(),
        patterns.runningFold(0) { a, p -> a + p.starts.size }.toIntArray(), patterns.flatMap { it.starts }.toIntArray(),
        patterns.flatMap { p -> p.starts.map { p.service } }.toIntArray(),
        shapeStart.toIntArray(), deltas { it.first }, deltas { it.second },
        IntArray(patterns.size) { 100 + it }, IntArray(patterns.size),
    )
}
