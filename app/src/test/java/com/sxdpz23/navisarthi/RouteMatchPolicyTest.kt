package com.sxdpz23.navisarthi
import com.sxdpz23.navisarthi.dash.nav.RouteMatchPolicy
import org.junit.Assert.*
import org.junit.Test

class RouteMatchPolicyTest {
    @Test fun alignedDriftRetainsRouteButWrongTurnReleasesIt() {
        val p = RouteMatchPolicy(); val r = Any()
        assertTrue(p.accepts(r, 22.0, 20f, 5f, false, true, false))
        assertTrue(p.accepts(r, 38.0, 20f, 5f, false, true, false))
        assertFalse(p.accepts(r, 20.0, 20f, 70f, false, true, false))
    }
    @Test fun largeDriftAndBadFixesCannotForceRoutePosition() {
        val p = RouteMatchPolicy(); val r = Any()
        assertFalse(p.accepts(r, 60.0, 20f, 0f, false, true, false))
        assertFalse(p.accepts(r, 10.0, 45f, 0f, false, true, false))
        assertFalse(p.accepts(r, 10.0, 20f, 0f, false, false, false))
        assertFalse(p.accepts(r, 10.0, 20f, 0f, false, true, true))
    }
    @Test fun changedRouteAndMissingHeadingDoNotReusePreviousConfidence() {
        val p = RouteMatchPolicy(); val r = Any()
        assertTrue(p.accepts(r, 20.0, 20f, 0f, false, true, false))
        assertFalse(p.accepts(Any(), 38.0, 20f, 0f, false, true, false))
        assertFalse(p.accepts(r, 20.0, 20f, null, false, true, false))
        assertFalse(p.accepts(null, 0.0, 20f, null, true, true, false))
    }
}
