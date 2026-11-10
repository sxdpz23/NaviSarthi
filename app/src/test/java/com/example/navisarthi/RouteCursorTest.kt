package com.example.navisarthi

import com.example.navisarthi.dash.nav.*
import org.junit.Assert.*
import org.junit.Test

class RouteCursorTest {
    private fun route(vararg points: GeoPoint): Route {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size)
            cumulative[i] = cumulative[i - 1] + GeoPoint.distMeters(points[i - 1], points[i])
        return Route(points.toList(), emptyList(), cumulative.lastOrNull() ?: 0.0, 0.0, cumulative)
    }
    private fun assertOnRoute(route: Route, pose: RouteCursor.Pose) {
        val separation = route.geometry.zipWithNext().minOf { (a, b) ->
            GeoPoint.distMeters(pose.point, GeoPoint.projectOnSegment(pose.point, a, b).first)
        }
        assertEquals(0.0, separation, 0.001)
        assertEquals(pose.point, pose.remaining.first())
    }
    private val bend = route(GeoPoint(0.0, 0.0), GeoPoint(0.0, .001), GeoPoint(.001, .001))

    @Test fun predictionFollowsBendInsteadOfContinuingAlongOldBearing() {
        val pose = RouteCursor().update(bend, bend.cumulative[1] - 5.0, 20.0, 1.0)!!
        assertOnRoute(bend, pose)
        assertEquals(.001, pose.point.lng, 1e-10)
        assertTrue(pose.point.lat > 0.0)
        assertEquals(0f, pose.bearing, .01f)
    }

    @Test fun smoothingAcrossCornerNeverCutsDiagonallyAcrossTheRoad() {
        val cursor = RouteCursor()
        cursor.update(bend, 60.0, 0.0, 1.0)
        repeat(25) {
            val pose = cursor.update(bend, 180.0, 0.0, .2)!!
            assertOnRoute(bend, pose)
            // A coordinate-space interpolation would have both coordinates inside the bend.
            assertTrue(pose.point.lat == 0.0 || pose.point.lng == .001)
        }
    }

    @Test fun routeLineStartsAtDisplayedPointEvenWhenGpsIsAheadOfSmoothing() {
        val cursor = RouteCursor()
        cursor.update(bend, 10.0, 0.0, 1.0)
        val pose = cursor.update(bend, 80.0, 0.0, .1)!!
        assertEquals(17.0, pose.meters, 1e-9)
        assertOnRoute(bend, pose)
        assertEquals(bend.geometry[1], pose.remaining[1])
    }

    @Test fun curvedAndUTurnRoutesRemainConstrainedForRepeatedUpdates() {
        val curve = route(GeoPoint(0.0, 0.0), GeoPoint(0.0, .001), GeoPoint(.0003, .0013),
            GeoPoint(.0006, .001), GeoPoint(.0006, 0.0))
        val cursor = RouteCursor()
        repeat(100) { index ->
            assertOnRoute(curve, cursor.update(curve, index * curve.totalMeters / 99, 12.0, .3)!!)
        }
    }

    @Test fun endOfRouteClampsPredictionAndHasNoPhantomRemainingSegment() {
        val pose = RouteCursor().update(bend, bend.totalMeters - 2, 100.0, 1.0)!!
        assertEquals(bend.destination, pose.point)
        assertEquals(listOf(bend.destination), pose.remaining)
        assertEquals(bend.totalMeters, pose.meters, 0.0)
    }

    @Test fun replacementRouteAndReacquisitionResetOldDistanceSmoothing() {
        val cursor = RouteCursor()
        cursor.update(bend, 190.0, 0.0, 1.0)
        val replacement = route(GeoPoint(1.0, 1.0), GeoPoint(1.0, 1.002))
        val pose = cursor.update(replacement, 15.0, 0.0, .01)!!
        assertEquals(15.0, pose.meters, 0.0)
        assertOnRoute(replacement, pose)
        cursor.clear() // GPS/direction/accuracy no longer supports snapping.
        assertEquals(80.0, cursor.update(replacement, 80.0, 0.0, .01)!!.meters, 0.0)
    }

    @Test fun duplicateVerticesAndStationaryFixesRemainFiniteAndStable() {
        val duplicate = route(bend.geometry[0], bend.geometry[0], bend.geometry[1], bend.geometry[1], bend.geometry[2])
        val cursor = RouteCursor()
        val first = cursor.update(duplicate, duplicate.cumulative[2], 0.0, 1.0)!!
        repeat(10) { assertEquals(first.point, cursor.update(duplicate, duplicate.cumulative[2], 0.0, .2)!!.point) }
        assertOnRoute(duplicate, first)
        assertTrue(first.bearing.isFinite())
    }

    @Test fun invalidGeometryOrInputsReturnControlToGpsFallback() {
        val cursor = RouteCursor()
        assertNull(cursor.update(route(GeoPoint(0.0, 0.0)), 0.0, 0.0, 1.0))
        assertNull(cursor.update(route(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0)), 0.0, 0.0, 1.0))
        assertNull(cursor.update(bend, Double.NaN, 0.0, 1.0))
        assertNull(cursor.update(bend, 0.0, Double.POSITIVE_INFINITY, 1.0))
        assertNull(cursor.update(bend.copy(cumulative = doubleArrayOf(0.0, 200.0, 100.0)), 10.0, 0.0, 1.0))
    }
}
