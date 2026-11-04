package com.yourway.app.data

import com.yourway.app.routing.LiveSource
import com.yourway.app.routing.LiveUpdater
import com.yourway.app.routing.Schedule
import com.yourway.app.routing.TripPlanner

/** Calculates routes on the phone from the schedule shipped with the app. */
class TripRepository(
    private val loadSchedule: suspend () -> Schedule,
    private val stops: suspend () -> StopDirectory,
    private val live: LiveSource? = null,
) {
    /** Adds live bus delays to routes that start soon; returns the routes unchanged when there is nothing live. */
    suspend fun withLiveData(trips: List<Itinerary>, now: java.time.LocalDateTime): List<Itinerary> {
        val source = live ?: return trips
        if (trips.none { it.departure <= now.plusMinutes(LIVE_WINDOW_MINUTES) && it.departure.toLocalDate() == now.toLocalDate() }) return trips
        return LiveUpdater(loadSchedule(), stops(), source).apply(trips)
    }

    suspend fun plan(query: TripQuery): List<Itinerary> {
        val schedule = loadSchedule()
        val directory = stops()
        check(schedule.stopCount == directory.count) { "Schedule and stop list are from different downloads" }
        val day = query.time.year * 10000 + query.time.monthValue * 100 + query.time.dayOfMonth
        if (day < schedule.firstDate || day > schedule.lastDate) throw ScheduleExpiredException()
        return TripPlanner(schedule, directory).plan(query)
    }

    private companion object {
        const val LIVE_WINDOW_MINUTES = 90L
    }
}
