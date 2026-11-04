package com.yourway.app

import com.yourway.app.data.Itinerary
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TripQuery
import com.yourway.app.routing.LiveSource
import com.yourway.app.routing.LiveUpdater
import com.yourway.app.routing.TripPlanner
import com.yourway.app.routing.VehicleFix
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdatesTest {
    private val coordinates = listOf(32.00 to 34.8, 32.01 to 34.8, 32.02 to 34.8, 32.03 to 34.8)
    private val stops = testStops(coordinates)
    private val schedule = testSchedule(4, listOf(TestPattern("8", listOf(0, 1, 2, 3), listOf(0, 300, 600, 900), listOf(8 * 3600))))
    private val now = Instant.parse("2026-11-02T06:05:00Z") // 08:05 in Israel
    private val monday = LocalDateTime.of(2026, 11, 2, 7, 50)

    private fun trips(): List<Itinerary> {
        val from = Place("a", "", 32.02, 34.8, PlaceKind.ADDRESS)
        val to = Place("b", "", 32.0301, 34.8, PlaceKind.ADDRESS)
        return TripPlanner(schedule, stops).plan(TripQuery(from, to, TimeMode.LEAVE_AT, monday, 1000, hebrew = false))
    }

    private fun live(fix: VehicleFix?, asked: MutableList<Int> = ArrayList()) = LiveUpdater(
        schedule, stops,
        object : LiveSource { override fun latestFix(lineRef: Int, scheduledStart: Instant): VehicleFix? { asked += lineRef; return fix } },
        now = { now },
    )

    @Test
    fun aBusTwoMinutesBehindMovesTheRideTwoMinutesLater() = runBlocking {
        val planned = trips()
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 10), planned.first().rides.single().start)
        // Halfway to the second stop the timetable says 150 s; it is 270 s after the trip's start: 120 s late.
        val asked = ArrayList<Int>()
        val updated = live(VehicleFix(32.005, 34.8, Instant.parse("2026-11-02T06:04:30Z")), asked).apply(planned).first()
        val ride = updated.rides.single()
        assertTrue(ride.isLive)
        assertEquals(2, ride.delayMinutes)
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 12), ride.start)
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 17), ride.end)
        assertEquals(listOf(100), asked) // asked about the GTFS route number
    }

    @Test
    fun aBusThatAlreadyPassedYourStopOrHasNoReportKeepsItsSchedule() = runBlocking {
        val past = live(VehicleFix(32.025, 34.8, Instant.parse("2026-11-02T06:04:30Z"))).apply(trips()).first().rides.single()
        assertFalse(past.isLive)
        val none = live(null).apply(trips()).first().rides.single()
        assertFalse(none.isLive)
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 10), none.start)
    }

    @Test
    fun oldReportsAndFarAwayBusesAreIgnored() = runBlocking {
        val old = live(VehicleFix(32.005, 34.8, Instant.parse("2026-11-02T05:40:00Z"))).apply(trips()).first().rides.single()
        assertFalse(old.isLive)
        val far = live(VehicleFix(32.005, 35.2, Instant.parse("2026-11-02T06:04:30Z"))).apply(trips()).first().rides.single()
        assertFalse(far.isLive)
    }
}
