package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.core.model.Maneuver
import com.sxdpz23.navisarthi.navigation.core.model.ManeuverType
import com.sxdpz23.navisarthi.navigation.core.model.Route
import com.sxdpz23.navisarthi.navigation.core.model.RouteLeg
import com.sxdpz23.navisarthi.dash.nav.NavigationRouting
import org.junit.Assert.*
import org.junit.Test

class NavigationRoutingTest {
    private fun step(type: ManeuverType, point: LatLng) = Maneuver(type, type.name, point, 111.0, 10.0)
    private fun route(points: List<LatLng>, legs: List<RouteLeg>) = Route(polyline = points, legs = legs,
        distanceMeters = legs.sumOf { it.distanceMeters }, durationSeconds = 80.0, durationInTrafficSeconds = 95.0)

    @Test fun keepsTrafficTimingAndGuidance() {
        val points = listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0), LatLng(12.002, 77.0))
        val source = route(points, listOf(RouteLeg(222.0, 80.0, 95.0, listOf(
            step(ManeuverType.DEPART, points[0]), step(ManeuverType.FORK_LEFT, points[1]), step(ManeuverType.ARRIVE, points[2])))))
        val result = NavigationRouting.consoleRoute(source)
        assertEquals(95.0, result.totalSeconds, .001)
        assertEquals(com.sxdpz23.navisarthi.dash.nav.ManeuverType.SLIGHT_LEFT, result.maneuvers[1].type)
        assertTrue(result.maneuvers.zipWithNext().all { (a,b) -> b.cumulativeMeters >= a.cumulativeMeters })
        assertEquals(result.geometry.size, result.cumulative.size)
    }

    @Test fun splitsStopsAndResetsLegDistance() {
        val points = (0..4).map { LatLng(12.0 + it * .001, 77.0) }
        val legs = listOf(RouteLeg(222.0, 30.0, 40.0, listOf(step(ManeuverType.DEPART, points[0]), step(ManeuverType.ARRIVE, points[2]))),
            RouteLeg(222.0, 50.0, 55.0, listOf(step(ManeuverType.DEPART, points[2]), step(ManeuverType.ARRIVE, points[4]))))
        val result = NavigationRouting.consoleLegs(route(points, legs))
        assertEquals(2, result.size)
        assertEquals(result[0].destination, result[1].geometry.first())
        assertEquals(0.0, result[1].cumulative.first(), 0.0)
        assertEquals(55.0, result[1].totalSeconds, .001)
        assertEquals(points.last().lat, result.last().destination!!.lat, 0.0)
    }

    @Test fun repeatedCoordinateDoesNotMoveInstructionsBackwards() {
        val a=LatLng(12.0,77.0); val b=LatLng(12.001,77.0); val c=LatLng(12.002,77.0)
        val points=listOf(a,b,c,b,a)
        val source=route(points,listOf(RouteLeg(444.0,80.0,null,listOf(step(ManeuverType.DEPART,a),
            step(ManeuverType.UTURN,c),step(ManeuverType.CONTINUE,b),step(ManeuverType.ARRIVE,a)))))
        val result=NavigationRouting.consoleRoute(source)
        assertTrue(result.maneuvers[2].cumulativeMeters > result.maneuvers[1].cumulativeMeters)
        assertEquals(result.cumulative.last(), result.maneuvers.last().cumulativeMeters, .01)
    }
}
