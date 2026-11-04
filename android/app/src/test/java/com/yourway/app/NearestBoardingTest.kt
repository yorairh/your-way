package com.yourway.app

import com.yourway.app.data.Itinerary
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.TimeMode
import com.yourway.app.data.TripQuery
import com.yourway.app.routing.TripPlanner
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The passenger stands at 32.0, 34.8. The far stop is about 984 m of estimated walking away, the near stop about 14 m,
 * and both lines end at a stop a few metres from the destination.
 */
class NearestBoardingTest {
    private val far = 32.0068 to 34.8
    private val near = 32.0001 to 34.8
    private val end = 32.3 to 34.8
    private val coordinates = listOf(far, near, end)
    private val stops = testStops(coordinates)
    private val monday = LocalDateTime.of(2026, 11, 2, 8, 0)
    private val eight = 8 * 3600

    private fun plan(vararg patterns: TestPattern, walk: Int = 1000, stopsAt: List<Pair<Double, Double>> = coordinates): List<Itinerary> {
        val stops = if (stopsAt === coordinates) stops else testStops(stopsAt)
        val schedule = testSchedule(stopsAt.size, patterns.toList())
        val from = Place("home", "", 32.0, 34.8, PlaceKind.ADDRESS)
        val to = Place("work", "", 32.3001, 34.8, PlaceKind.ADDRESS)
        return TripPlanner(schedule, stops).plan(TripQuery(from, to, TimeMode.LEAVE_AT, monday, walk, hebrew = false))
    }

    @Test
    fun beingWithinTwentyMetresOfAStopMeansNoWalkIsShown() {
        // The near stop is about 14 m from the start, so the trip should open with the bus, not a walk.
        val trip = plan(TestPattern("7", listOf(0, 1, 2), listOf(0, 120, 1200), listOf(eight + 1500))).first()
        assertTrue(trip.legs.first() is com.yourway.app.data.TransitLeg)
    }

    @Test
    fun boardsTheSameBusAtTheStopYouAreStandingAt() {
        val trip = plan(TestPattern("7", listOf(0, 1, 2), listOf(0, 120, 1200), listOf(eight + 1500))).first()
        val ride = trip.rides.single()
        assertEquals("stop1", ride.fromStop)
        assertTrue("walks ${trip.walkMeters} m", trip.walkMeters < 100)
        // Same bus either way, so the times don't change: it leaves the near stop two minutes after the far one.
        assertEquals(monday.plusMinutes(27), ride.start)
        assertEquals(monday.plusMinutes(45), ride.end)
    }

    @Test
    fun aSlightlyLaterBusFromANearbyStopBeatsAWalkToAnEarlierOne() {
        val trips = plan(
            TestPattern("far", listOf(0, 2), listOf(0, 900), listOf(eight + 1200)),
            TestPattern("near", listOf(1, 2), listOf(0, 900), listOf(eight + 1500)),
        )
        assertEquals(listOf("near", "far"), trips.map { it.rides.single().line })
    }

    @Test
    fun theSameArrivalGoesToTheRouteWithLessWalking() {
        val trips = plan(
            TestPattern("far", listOf(0, 2), listOf(0, 900), listOf(eight + 1200)),
            TestPattern("near", listOf(1, 2), listOf(0, 900), listOf(eight + 1200)),
        )
        assertEquals("near", trips.first().rides.single().line)
    }

    @Test
    fun aMuchLaterBusIsNotWorthTheShorterWalk() {
        val trips = plan(
            TestPattern("far", listOf(0, 2), listOf(0, 900), listOf(eight + 1200)),
            TestPattern("near", listOf(1, 2), listOf(0, 900), listOf(eight + 4500)),
        )
        assertEquals("far", trips.first().rides.single().line)
    }

    @Test
    fun theFarStopIsStillUsedWhenItIsTheOnlyWayThere() {
        val trips = plan(TestPattern("far", listOf(0, 2), listOf(0, 900), listOf(eight + 1200)))
        assertEquals("stop0", trips.first().rides.single().fromStop)
    }

    @Test
    fun picksTheNearerStopOfTheSameBusEvenWhenNoShorterSearchWouldReachEither() {
        // About 955 m and 521 m of walking: both inside the 1000 m limit, both outside half of it.
        val stopsAt = listOf(32.0066 to 34.8, 32.0036 to 34.8, end)
        val trip = plan(TestPattern("7", listOf(0, 1, 2), listOf(0, 120, 1200), listOf(eight + 1500)), stopsAt = stopsAt).first()
        assertEquals("stop1", trip.rides.single().fromStop)
        assertTrue("walks ${trip.walkMeters} m", trip.walkMeters < 600)
    }
}
