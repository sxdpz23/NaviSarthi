package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.nav.*
import org.junit.Assert.*
import org.junit.Test

class RerouteDirectionTest {
    private val origin = GeoPoint(0.0, 0.0)
    private fun fix(time: Long, bearing: Float? = 90f, speed: Float = 10f, accuracy: Float = 5f,
        point: GeoPoint = origin, bearingAccuracy: Float? = 5f) =
        RerouteDirection.Fix(point, bearing, speed, accuracy, time, bearingAccuracy)

    @Test fun usesActualTravelBearingAndWrapsNorthCorrectly() {
        val direction = RerouteDirection()
        assertEquals(90, direction.heading(fix(1000), 1000))
        assertEquals(0, direction.heading(fix(2000, 359.8f), 2000))
        assertEquals(270, direction.heading(fix(3000, -90f), 3000))
    }
    @Test fun stoppedRiderCanUseRecentHeadingButStationaryBearingNoiseCannotReplaceIt() {
        val direction = RerouteDirection()
        assertEquals(90, direction.heading(fix(1000), 1000))
        assertEquals(90, direction.heading(fix(2000, 270f, speed = 0f), 2000))
        assertNull(direction.heading(fix(32000, speed = 0f), 32000))
        direction.clear()
        assertNull(direction.heading(fix(33000, speed = 0f), 33000))
    }
    @Test fun staleInaccurateOrUncertainBearingDoesNotInventADirection() {
        assertNull(RerouteDirection().heading(fix(1000), 6000))
        assertNull(RerouteDirection().heading(fix(1000, accuracy = 30f), 1000))
        assertNull(RerouteDirection().heading(fix(1000, bearingAccuracy = 60f), 1000))
        assertNull(RerouteDirection().heading(fix(1000, bearing = null), 1000))
        assertNull(RerouteDirection().heading(fix(2000), 1000))
        val direction = RerouteDirection()
        direction.heading(fix(1000), 1000)
        assertNull(direction.heading(fix(2000, speed = 0f, point = GeoPoint(0.0, .001)), 2000))
    }
    private fun route(east: Boolean, uTurnMeters: Double? = null): Route {
        val points = listOf(origin, GeoPoint(0.0, if (east) .001 else -.001))
        val distance = GeoPoint.distMeters(points[0], points[1])
        return Route(points, uTurnMeters?.let {
            listOf(Maneuver(ManeuverType.UTURN, "Make a U-turn", points[1], it))
        }.orEmpty(), distance, 30.0, doubleArrayOf(0.0, distance))
    }
    @Test fun acceptsForwardDepartureAndLaterUTurnButRejectsBackwardsOrImmediateUTurn() {
        assertTrue(DepartureDirection.accepts(route(true), 90))
        assertFalse(DepartureDirection.accepts(route(false), 90))
        assertFalse(DepartureDirection.accepts(route(true, 0.0), 90))
        assertFalse(DepartureDirection.accepts(route(true, 5.0), 90))
        assertTrue(DepartureDirection.accepts(route(true, 80.0), 90))
    }
    @Test fun osrmRestrictsOnlyOriginAndLeavesDestinationDirectionFree() {
        assertEquals("&bearings=90,60;", DepartureDirection.osrmOption(90))
    }
}
