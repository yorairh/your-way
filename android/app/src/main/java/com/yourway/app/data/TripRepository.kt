package com.yourway.app.data

import com.yourway.app.routing.Schedule
import com.yourway.app.routing.TripPlanner

/** Calculates routes on the phone from the schedule shipped with the app. */
class TripRepository(
    private val loadSchedule: suspend () -> Schedule,
    private val stops: suspend () -> StopDirectory,
) {
    suspend fun plan(query: TripQuery): List<Itinerary> {
        val schedule = loadSchedule()
        val directory = stops()
        check(schedule.stopCount == directory.count) { "Schedule and stop list are from different downloads" }
        val day = query.time.year * 10000 + query.time.monthValue * 100 + query.time.dayOfMonth
        if (day < schedule.firstDate || day > schedule.lastDate) throw ScheduleExpiredException()
        return TripPlanner(schedule, directory).plan(query)
    }
}
