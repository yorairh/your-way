package com.yourway.app

import com.yourway.app.data.Geo
import com.yourway.app.data.Itinerary
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.StopDirectory
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.TripQuery
import com.yourway.app.data.WalkLeg
import com.yourway.app.routing.Schedule
import com.yourway.app.routing.TripPlanner
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Runs real trips on the schedule that ships in the app, and prints them. */
class RealScheduleTest {
    private fun place(name: String, lat: Double, lon: Double) = Place(name, "", lat, lon, PlaceKind.ADDRESS)

    private val telAviv = place("Tel Aviv central station", 32.0560, 34.7796)
    private val jerusalem = place("Jerusalem central station", 31.7890, 35.2037)
    private val savidor = place("Savidor Center", 32.0836, 34.7981)
    private val navon = place("Navon", 31.7887, 35.2030)
    private val haifa = place("Haifa Hashmona", 32.7943, 34.9996)
    private val beerSheva = place("Beer Sheva", 31.2430, 34.7983)
    private val herzl = place("Mount Herzl", 31.7745, 35.1818)
    private val ammunitionHill = place("Ammunition Hill", 31.7945, 35.2234)
    private val wednesday = LocalDateTime.of(2026, 11, 4, 8, 0)

    private fun plan(from: Place, to: Place, time: LocalDateTime, mode: TimeMode = TimeMode.LEAVE_AT, walk: Int = 1000): List<Itinerary> {
        val started = System.nanoTime()
        val trips = planner.plan(TripQuery(from, to, mode, time, walk, hebrew = false))
        println("${from.name} -> ${to.name} $time $mode walk=$walk: ${trips.size} trips in ${(System.nanoTime() - started) / 1_000_000} ms")
        trips.forEach { trip ->
            println("  ${trip.departure.toLocalTime()} -> ${trip.arrival.toLocalTime()} (${trip.duration.toMinutes()} min, ${trip.transfers} transfers, walk ${trip.walkMeters} m)")
            trip.legs.forEach { leg ->
                when (leg) {
                    is WalkLeg -> println("     walk ${leg.meters} m to ${leg.toName}")
                    is TransitLeg -> println("     ${leg.start.toLocalTime()}-${leg.end.toLocalTime()} ${leg.mode} ${leg.line}: ${leg.fromStop} > ${leg.toStop} (${leg.stopCount} stops, ${leg.path.size} map points)")
                }
            }
        }
        return trips
    }

    @Test
    fun telAvivToJerusalemOnAWeekdayMorning() {
        val trips = plan(telAviv, jerusalem, wednesday)
        assertEquals(3, trips.size)
        assertTrue(trips.any { t -> t.rides.any { it.mode == TransitMode.TRAIN } })
        trips.forEach { checkTrip(it) }
    }

    @Test
    fun savidorToNavonIsADirectTrain() {
        val trips = plan(savidor, navon, wednesday)
        assertEquals(0, trips.first().transfers)
        assertEquals(TransitMode.TRAIN, trips.first().rides.single().mode)
    }

    @Test
    fun noTrainsRunOnSaturdayDaytime() {
        val trips = plan(telAviv, jerusalem, LocalDateTime.of(2026, 11, 7, 11, 0))
        assertTrue(trips.isNotEmpty())
        val earlyTrain = trips.flatMap { it.rides }.filter { it.mode == TransitMode.TRAIN && it.start.hour < 18 }
        assertTrue("trains before Shabbat ended: ${earlyTrain.size}", earlyTrain.isEmpty())
    }

    @Test
    fun otherLongAndShortTrips() {
        listOf(plan(haifa, beerSheva, wednesday), plan(herzl, ammunitionHill, wednesday)).forEach { trips ->
            assertTrue(trips.isNotEmpty())
            trips.forEach { checkTrip(it) }
        }
    }

    @Test
    fun arriveByAndAShortWalkLimit() {
        val arrive = plan(telAviv, jerusalem, wednesday.plusHours(2), TimeMode.ARRIVE_BY)
        assertTrue(arrive.isNotEmpty())
        arrive.forEach { assertFalse(it.arrival.isAfter(wednesday.plusHours(2))) }
        plan(telAviv, jerusalem, wednesday, walk = 300).forEach { trip ->
            trip.legs.filterIsInstance<WalkLeg>().forEach { assertTrue(it.meters <= 300) }
        }
    }

    /** Times in order, legs joined up, and every ride has a line to draw. */
    private fun checkTrip(trip: Itinerary) {
        trip.legs.zipWithNext().forEach { (a, b) -> assertFalse(b.start.isBefore(a.end)) }
        trip.rides.forEach { ride ->
            assertTrue(ride.path.size >= 2)
            val length = ride.path.zipWithNext().sumOf { (p, q) -> Geo.straightMeters(p.lat, p.lon, q.lat, q.lon) }
            assertTrue("a ride is drawn as only $length m", length > 100)
        }
        trip.legs.zipWithNext().forEach { (a, b) ->
            val end = when (a) { is WalkLeg -> a.to; is TransitLeg -> a.path.last() }
            val start = when (b) { is WalkLeg -> b.from; is TransitLeg -> b.path.first() }
            assertTrue("legs are ${Geo.straightMeters(end.lat, end.lon, start.lat, start.lon)} m apart", Geo.straightMeters(end.lat, end.lon, start.lat, start.lon) < 60)
        }
    }

    companion object {
        private lateinit var planner: TripPlanner

        @BeforeClass
        @JvmStatic
        fun load() {
            val started = System.nanoTime()
            val stops = File("src/main/assets/stops.dat").inputStream().use { StopDirectory.read(it) }
            val schedule = File("src/main/assets/schedule.dat").inputStream().use { Schedule.read(it) }
            println("loaded in ${(System.nanoTime() - started) / 1_000_000} ms: ${stops.count} stops, ${schedule.patternCount} patterns")
            assertEquals(stops.count, schedule.stopCount)
            planner = TripPlanner(schedule, stops)
        }
    }
}
