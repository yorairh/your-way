package com.yourway.app

import com.yourway.app.data.Itinerary
import com.yourway.app.data.LatLon
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TripQuery
import com.yourway.app.data.WalkLeg
import com.yourway.app.routing.Schedule
import com.yourway.app.routing.TripPlanner
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerTest {
    // Stops are about 11 km apart, so nothing is walkable between them.
    private val a = 32.0 to 34.8
    private val b = 32.1 to 34.8
    private val c = 32.2 to 34.8
    private val d = 32.3 to 34.8
    private val coordinates = listOf(a, b, c, d)
    private val stops = testStops(coordinates)

    // Monday 2 November 2026 at 08:00
    private val monday = LocalDateTime.of(2026, 11, 2, 8, 0)
    private val eight = 8 * 3600

    private fun near(p: Pair<Double, Double>, name: String) = Place(name, "", p.first + 0.001, p.second, PlaceKind.ADDRESS)

    private fun plan(
        schedule: Schedule,
        from: Pair<Double, Double> = a,
        to: Pair<Double, Double> = c,
        time: LocalDateTime = monday,
        mode: TimeMode = TimeMode.LEAVE_AT,
        walk: Int = 1000,
    ) = TripPlanner(schedule, stops).plan(TripQuery(near(from, "from"), near(to, "to"), mode, time, walk, hebrew = false))

    private fun schedule(vararg patterns: TestPattern) = testSchedule(coordinates.size, patterns.toList())

    private fun lines(i: Itinerary) = i.rides.map { it.line }

    @Test
    fun findsDirectRideAndTheNextDeparture() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 1800), listOf(eight - 600, eight + 600)))
        val best = plan(s).first()
        assertEquals(listOf("1"), lines(best))
        val ride = best.rides.single()
        assertEquals(monday.plusMinutes(10), ride.start)
        assertEquals(monday.plusMinutes(40), ride.end)
    }

    @Test
    fun transfersAfterTheTwoMinuteMargin() {
        val s = schedule(
            TestPattern("1", listOf(0, 1), listOf(0, 600), listOf(eight + 300)),
            TestPattern("2", listOf(1, 2), listOf(0, 600), listOf(eight + 960, eight + 1080)),
        )
        val best = plan(s).first()
        assertEquals(listOf("1", "2"), lines(best))
        // Arrives at 08:15. The 08:16 is inside the margin, so it takes the 08:18.
        assertEquals(monday.plusMinutes(18), best.rides.last().start)
    }

    @Test
    fun skipsServiceThatDoesNotRunThatDay() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight + 300), service = 1))
        assertTrue(plan(s).isEmpty())
        assertEquals(1, plan(s, time = monday.minusDays(1)).size)
    }

    @Test
    fun respectsTheWalkingLimit() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight + 300)))
        assertFalse(plan(s, walk = 1000).isEmpty())
        assertTrue(plan(s, walk = 100).isEmpty())
    }

    @Test
    fun cannotBoardWhereThereIsNoPickup() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight + 300), flags = listOf(1, 0)))
        assertTrue(plan(s).isEmpty())
    }

    @Test
    fun findsATripListedPastMidnightOnTheServiceDayBefore() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(25 * 3600)))
        val ride = plan(s, time = LocalDateTime.of(2026, 11, 3, 0, 30)).first().rides.single()
        assertEquals(LocalDateTime.of(2026, 11, 3, 1, 0), ride.start)
    }

    @Test
    fun fewerTransfersWinsWhenArrivalIsClose() {
        val s = schedule(
            TestPattern("fast1", listOf(0, 1), listOf(0, 300), listOf(eight + 60)),
            TestPattern("fast2", listOf(1, 2), listOf(0, 300), listOf(eight + 600)),
            TestPattern("direct", listOf(0, 2), listOf(0, 1080), listOf(eight + 60)),
        )
        assertEquals(listOf("direct"), lines(plan(s).first()))
    }

    @Test
    fun arriveByReturnsARouteThatArrivesInTime() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight, eight + 3600)))
        val best = plan(s, time = monday.plusMinutes(30), mode = TimeMode.ARRIVE_BY).first()
        assertFalse(best.arrival.isAfter(monday.plusMinutes(30)))
        assertEquals(monday, best.rides.single().start)
    }

    @Test
    fun walksWhenTheDestinationIsClose() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight + 300)))
        assertTrue(plan(s, to = a).first().legs.single() is WalkLeg)
    }

    @Test
    fun offersOtherLinesInsteadOfLaterRunsOfTheBestLine() {
        val s = schedule(
            TestPattern("fast", listOf(0, 2), listOf(0, 600), (0..5).map { eight + 300 + it * 3600 }),
            TestPattern("slow", listOf(0, 2), listOf(0, 1800), listOf(eight + 600)),
        )
        assertEquals(listOf(listOf("fast"), listOf("slow")), plan(s).map { lines(it) })
    }

    @Test
    fun neverWalksTwiceInARow() {
        val close = listOf(32.0 to 34.8, 32.1 to 34.8, 32.102 to 34.8, 32.104 to 34.8)
        val s = testSchedule(close.size, listOf(TestPattern("1", listOf(0, 1), listOf(0, 600), listOf(eight + 300))))
        val trips = TripPlanner(s, testStops(close)).plan(
            TripQuery(near(close[0], "from"), near(close[3], "to"), TimeMode.LEAVE_AT, monday, 400, false),
        )
        assertTrue(trips.isEmpty())
    }

    @Test
    fun rideDrawsTheRoadShapeBetweenItsStopsOnly() {
        val shape = listOf(31.95 to 34.8, 32.0 to 34.8, 32.05 to 34.85, 32.1 to 34.8, 32.15 to 34.8, 32.2 to 34.8, 32.25 to 34.8)
        val s = schedule(TestPattern("1", listOf(0, 1, 2), listOf(0, 600, 1200), listOf(eight + 300), shape = shape))
        val path = plan(s).first().rides.single().path
        // From stop a through the bend at 32.05,34.85 and on to stop c, without the shape's ends.
        val shown = path.joinToString { "${it.lat},${it.lon}" }
        assertEquals(shown, 32.0, path.first().lat, 1e-4)
        assertEquals(shown, 32.2, path.last().lat, 1e-4)
        assertTrue(shown, path.any { Math.abs(it.lat - 32.05) < 1e-6 && Math.abs(it.lon - 34.85) < 1e-6 })
        assertTrue(shown, path.none { it.lat < 31.99 || it.lat > 32.21 })
    }

    @Test
    fun rideWithoutAShapeRunsStraightThroughItsStops() {
        val s = schedule(TestPattern("1", listOf(0, 1, 2), listOf(0, 600, 1200), listOf(eight + 300)))
        assertEquals(listOf(a.first, b.first, c.first), plan(s).first().rides.single().path.map { it.lat })
    }

    @Test
    fun walkingLegsCarryTheirEndpointsForTheMap() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), listOf(eight + 300)))
        val walks = plan(s).first().legs.filterIsInstance<WalkLeg>()
        assertEquals(2, walks.size)
        assertEquals(a.first + 0.001, walks.first().from.lat, 1e-9)
        assertEquals(c.first + 0.001, walks.last().to.lat, 1e-9)
    }

    @Test
    fun everyTripHasTimesInOrder() {
        val s = schedule(TestPattern("1", listOf(0, 2), listOf(0, 600), (0..3).map { eight + it * 900 }))
        for (trip in plan(s)) {
            trip.legs.zipWithNext().forEach { (x, y) -> assertFalse(y.start.isBefore(x.end)) }
            assertTrue(trip.legs.first() is WalkLeg || trip.legs.first() is TransitLeg)
        }
    }
}
