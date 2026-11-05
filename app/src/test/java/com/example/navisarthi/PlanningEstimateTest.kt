package com.example.navisarthi

import com.example.navisarthi.dash.nav.PlanningEstimate
import com.example.navisarthi.dash.nav.RouteMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningEstimateTest {
    @Test fun sameDistanceUsesRequestedVehicleAverages() {
        assertEquals(7200.0, PlanningEstimate.seconds(100_000.0, RouteMode.Car), 0.001)
        assertEquals(6000.0, PlanningEstimate.seconds(100_000.0, RouteMode.Bike), 0.001)
    }

    @Test fun multipleStopsUseTotalDistance() {
        val legs = listOf(12_500.0, 37_500.0, 50_000.0)
        for (mode in RouteMode.entries) {
            assertEquals(PlanningEstimate.seconds(legs.sum(), mode),
                legs.sumOf { PlanningEstimate.seconds(it, mode) }, 0.001)
        }
    }

    @Test fun shortAndEmptyRoutesPreservePrecision() {
        assertEquals(18.0, PlanningEstimate.seconds(250.0, RouteMode.Car), 0.001)
        assertEquals(0.0, PlanningEstimate.seconds(0.0, RouteMode.Bike), 0.001)
    }
}
