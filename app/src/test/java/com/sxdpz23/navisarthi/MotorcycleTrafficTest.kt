package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.core.data.MotorcycleTraffic
import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.core.model.Route
import com.sxdpz23.navisarthi.navigation.core.model.TrafficSpan
import org.junit.Assert.*
import org.junit.Test

class MotorcycleTrafficTest {
    private val road = (0..10).map { LatLng(12.0, 77.0 + it * .001) }
    private fun route(points: List<LatLng> = road, meters: Double = 1_090.0) =
        Route(polyline = points, legs = emptyList(), distanceMeters = meters, durationSeconds = 120.0, durationInTrafficSeconds = null)

    @Test fun matchingTrafficNeverReplacesMotorcycleGeometryOrTiming() {
        val bike = route()
        val congestion = route().copy(durationSeconds = 600.0, durationInTrafficSeconds = 900.0,
            trafficSpans = listOf(TrafficSpan(2, 100.0, 300.0)))
        val result = MotorcycleTraffic.overlay(bike, listOf(congestion))
        assertSame(bike.polyline, result.polyline)
        assertSame(bike.legs, result.legs)
        assertEquals(120.0, result.durationSeconds, 0.0)
        assertNull(result.durationInTrafficSeconds)
        assertEquals(congestion.trafficSpans, result.trafficSpans)
    }

    @Test fun congestionFromAnotherRoadIsNotAppliedToMotorcycleRoute() {
        val bike = route()
        val different = route(road.map { it.copy(lat = it.lat + .02) })
            .copy(trafficSpans = listOf(TrafficSpan(3, 100.0, 300.0)))
        assertSame(bike, MotorcycleTraffic.overlay(bike, listOf(different)))
        assertSame(bike, MotorcycleTraffic.overlay(bike, emptyList()))
    }

    @Test fun matchingOffsetsScaleToMotorcycleDistanceAndOfflineRoutesStayOffline() {
        val bike = route()
        val traffic = route(meters = 1_095.0).copy(trafficSpans = listOf(TrafficSpan(1, 100.0, 300.0)))
        val result = MotorcycleTraffic.overlay(bike, listOf(traffic))
        assertEquals(100.0 * 1_090 / 1_095, result.trafficSpans.single().startMeters, .001)
        assertEquals(300.0 * 1_090 / 1_095, result.trafficSpans.single().lengthMeters, .001)
        val offline = bike.copy(offline = true)
        assertSame(offline, MotorcycleTraffic.overlay(offline, listOf(traffic)))
    }
}
