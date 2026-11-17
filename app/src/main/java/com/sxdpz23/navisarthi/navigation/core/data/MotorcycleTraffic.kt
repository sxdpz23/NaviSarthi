package com.sxdpz23.navisarthi.navigation.core.data

import com.sxdpz23.navisarthi.navigation.core.model.Route

/** Congestion is display data: it must never replace a motorcycle's legal course or timing. */
internal object MotorcycleTraffic {
    fun overlay(route: Route, candidates: List<Route>): Route {
        if (route.polyline.size < 2 || route.distanceMeters <= 0.0 || route.offline) return route
        val traffic = candidates.firstOrNull {
            it.polyline.size >= 5 && it.distanceMeters > 0.0 && it.trafficSpans.isNotEmpty() &&
                !RouteGeometry.divergent(route, it)
        } ?: return route
        val scale = route.distanceMeters / traffic.distanceMeters
        return route.copy(trafficSpans = traffic.trafficSpans.map {
            it.copy(startMeters = it.startMeters * scale, lengthMeters = it.lengthMeters * scale)
        })
    }
}
