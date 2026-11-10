package com.example.navisarthi.dash.nav

/** Travel direction comes from reliable GPS movement, never the route we have just left. */
internal class RerouteDirection {
    data class Fix(val point: GeoPoint, val bearing: Float?, val speed: Float, val accuracy: Float,
        val elapsedMs: Long, val bearingAccuracy: Float? = null)
    private var lastMoving: Fix? = null

    fun clear() { lastMoving = null }

    fun heading(fix: Fix?, nowMs: Long): Int? {
        if (fix == null || nowMs - fix.elapsedMs !in 0L..4_000L ||
            !fix.accuracy.isFinite() || fix.accuracy <= 0f || fix.accuracy > 25f) return null
        if (fix.speed.isFinite() && fix.speed >= 2f && fix.bearing?.isFinite() == true &&
            (fix.bearingAccuracy == null || (fix.bearingAccuracy.isFinite() && fix.bearingAccuracy in 0f..30f))) {
            lastMoving = fix
        }
        val moving = lastMoving ?: return null
        if (nowMs - moving.elapsedMs !in 0L..30_000L || GeoPoint.distMeters(fix.point, moving.point) > 30.0) return null
        val degrees = ((moving.bearing!!.toDouble() % 360 + 360) % 360)
        return kotlin.math.round(degrees).toInt() % 360
    }
}

internal object DepartureDirection {
    const val TOLERANCE_DEGREES = 60

    fun osrmOption(heading: Int): String {
        require(heading in 0..359)
        // Destination heading is intentionally unrestricted (empty second entry).
        return "&bearings=$heading,$TOLERANCE_DEGREES;"
    }

    fun accepts(route: Route, heading: Int): Boolean {
        if (heading !in 0..359) return false
        // Skip sub-metre shape noise but do not average across a corner or a U-turn.
        val segment = route.geometry.zipWithNext().firstOrNull { (a, b) -> GeoPoint.distMeters(a, b) >= 1.0 }
            ?: return false
        val initial = GeoPoint.bearing(segment.first, segment.second)
        val delta = kotlin.math.abs((initial - heading + 540) % 360 - 180)
        if (delta > TOLERANCE_DEGREES) return false
        return route.maneuvers.none { it.type == ManeuverType.UTURN && it.cumulativeMeters <= 5.0 }
    }
}
