package com.example.navisarthi.dash.nav

/** User-selected planning assumptions only. Never used for live navigation ETA. */
object PlanningEstimate {
    fun averageKmh(mode: RouteMode): Int = when (mode) {
        RouteMode.Car -> 50
        RouteMode.Bike -> 60
    }

    fun seconds(distanceMeters: Double, mode: RouteMode): Double =
        distanceMeters.coerceAtLeast(0.0) * 3.6 / averageKmh(mode)
}
