package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.car.CarFraming
import com.sxdpz23.navisarthi.navigation.car.FrontClip
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class ConsoleFrontClipTest {
    private fun clip(zoom: Double, tilt: Double = 55.0, bearing: Double = 0.0): FrontClip {
        val mpp = 78271.517 * cos(Math.toRadians(20.0)) / Math.pow(2.0, zoom)
        return FrontClip(20.0, 73.0, bearing, CarFraming.behindLimitPx(tilt, 512.0) * mpp)
    }

    @Test fun distantRearRouteIsRejectedAtCloseZoomButVisibleWhenZoomedOut() {
        val rearLat = 20.0 - 500.0 / 111320.0
        assertFalse(clip(18.0).inFront(rearLat, 73.0))
        assertTrue(clip(16.0).inFront(rearLat, 73.0))
        assertTrue(clip(18.0).inFront(20.0 + 500.0 / 111320.0, 73.0))
    }

    @Test fun flatViewDoesNotLoseRearGeometryAndBearingReversesTheClippingSide() {
        val rearLat = 20.0 - 500.0 / 111320.0
        assertTrue(clip(18.0, tilt = 0.0).inFront(rearLat, 73.0))
        assertTrue(clip(18.0, bearing = 180.0).inFront(rearLat, 73.0))
    }

    @Test fun crossingStopsAtFrontPlaneInsteadOfJoiningMirroredPoints() {
        val plane = FrontClip(20.0, 73.0, 0.0, 100.0)
        val endLat = 20.0 - 400.0 / 111320.0
        val t = plane.crossing(20.0, 73.0, endLat, 73.0)
        assertEquals(.25, t, .00001)
        assertEquals(100.0, plane.behindM(20.0 + (endLat - 20.0) * t, 73.0), .00001)
    }
}
