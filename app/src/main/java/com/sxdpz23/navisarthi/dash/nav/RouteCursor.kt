package com.sxdpz23.navisarthi.dash.nav

/** Predict and smooth distance along a matched route, never a chord across its bends. */
internal class RouteCursor {
    data class Pose(val point: GeoPoint, val meters: Double, val bearing: Float,
        val remaining: List<GeoPoint>)

    private var activeRoute: Route? = null
    private var displayedMeters = 0.0

    fun clear() { activeRoute = null; displayedMeters = 0.0 }

    fun update(route: Route, matchedMeters: Double, predictionMeters: Double, alpha: Double): Pose? {
        val geometry = route.geometry
        val cumulative = route.cumulative
        if (geometry.size < 2 || cumulative.size != geometry.size ||
            !matchedMeters.isFinite() || !predictionMeters.isFinite() || !alpha.isFinite() ||
            !cumulative.last().isFinite() || cumulative.last() <= 0.0) {
            clear()
            return null
        }
        if (activeRoute !== route) {
            // Check once per route; interpolation and binary search require ordered distances.
            val valid = cumulative.first() == 0.0 && geometry.indices.all { index ->
                val point = geometry[index]
                point.lat.isFinite() && point.lng.isFinite() && cumulative[index].isFinite() &&
                    (index == 0 || cumulative[index] >= cumulative[index - 1])
            }
            if (!valid) { clear(); return null }
        }
        val target = (matchedMeters + predictionMeters.coerceAtLeast(0.0)).coerceIn(0.0, cumulative.last())
        displayedMeters = if (activeRoute !== route) target
            else displayedMeters + (target - displayedMeters) * alpha.coerceIn(0.0, 1.0)
        activeRoute = route

        // Upper bound skips duplicate vertices and chooses the outgoing segment at a corner.
        var low = 0
        var high = cumulative.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cumulative[middle] <= displayedMeters) low = middle + 1 else high = middle
        }
        val next = low.coerceAtMost(geometry.lastIndex)
        var start = (next - 1).coerceAtLeast(0)
        while (start > 0 && cumulative[next] <= cumulative[start]) start--
        val length = cumulative[next] - cumulative[start]
        if (length <= 0.0) { clear(); return null }
        val fraction = ((displayedMeters - cumulative[start]) / length).coerceIn(0.0, 1.0)
        val a = geometry[start]
        val b = geometry[next]
        val point = GeoPoint(a.lat + (b.lat - a.lat) * fraction, a.lng + (b.lng - a.lng) * fraction)
        val remaining = object : AbstractList<GeoPoint>() {
            override val size = 1 + geometry.size - low
            override fun get(index: Int): GeoPoint {
                if (index !in indices) throw IndexOutOfBoundsException(index.toString())
                return if (index == 0) point else geometry[low + index - 1]
            }
        }
        return Pose(point, displayedMeters, GeoPoint.bearing(a, b).toFloat(), remaining)
    }
}
