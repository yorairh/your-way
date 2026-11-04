package com.yourway.app.data

import kotlin.math.cos
import kotlin.math.sqrt

object Geo {
    const val METERS_PER_DEGREE = 111_320.0
    const val WALK_SPEED_MPS = 1.2

    /** Streets aren't straight lines, so straight-line distance is scaled up to estimate the walk. */
    const val WALK_FACTOR = 1.3

    /** Anything under this is treated as already being there (GPS and stop positions are never that exact). */
    const val AT_STOP_METERS = 20

    fun walkMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val meters = (straightMeters(lat1, lon1, lat2, lon2) * WALK_FACTOR).toInt()
        return if (meters < AT_STOP_METERS) 0 else meters
    }

    fun straightMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * METERS_PER_DEGREE
        val dx = (lon2 - lon1) * METERS_PER_DEGREE * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dx * dx + dy * dy)
    }

    fun walkSeconds(meters: Int): Int = (meters / WALK_SPEED_MPS).toInt()
}
