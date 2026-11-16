package com.sxdpz23.navisarthi.dash.nav

internal class RouteMatchPolicy {
    private var route: Any? = null
    private var matched = false
    fun accepts(identity: Any?, distance: Double, accuracy: Float, headingDelta: Float?, stopped: Boolean,
        usable: Boolean, offRoute: Boolean): Boolean {
        if (identity !== route) { route = identity; matched = false }
        val valid = identity != null && usable && !offRoute && distance.isFinite() && distance >= 0 &&
            accuracy.isFinite() && accuracy > 0f && accuracy <= 40f
        val aligned = headingDelta != null && headingDelta.isFinite() && headingDelta <= 40f
        val limit = when {
            aligned -> if (matched) (accuracy * 2.0).coerceIn(30.0, 50.0) else (accuracy * 1.5).coerceIn(25.0, 35.0)
            stopped && matched -> (accuracy * 1.5).coerceIn(15.0, 35.0)
            stopped && headingDelta == null -> accuracy.toDouble().coerceIn(6.0, 15.0)
            else -> 0.0
        }
        matched = valid && (aligned || (stopped && headingDelta == null)) && distance <= limit
        return matched
    }
}
