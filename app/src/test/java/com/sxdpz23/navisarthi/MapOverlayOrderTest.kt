package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.ui.map.MapOverlayOrder
import com.sxdpz23.navisarthi.navigation.ui.map.OverlayLayer
import org.junit.Assert.*
import org.junit.Test

class MapOverlayOrderTest {
    @Test fun interleavedOneWayArrowsDoNotPutRouteBelowBridges() {
        val layers = listOf(OverlayLayer("land", false), OverlayLayer("one-way", true),
            OverlayLayer("roads", false), OverlayLayer("bridge-casing", false), OverlayLayer("street-labels", true))
        assertEquals("bridge-casing", MapOverlayOrder.geometryTop(layers))
        assertEquals("street-labels", MapOverlayOrder.routeLabelAnchor(layers))
    }

    @Test fun arbitraryLookNamesAndRuntimeRouteLayersKeepTheirRelativeRoles() {
        val layers = listOf(OverlayLayer("background", false), OverlayLayer("custom-road-name", false),
            OverlayLayer("poi-text", true), OverlayLayer("navisarthi-route-halo", false), OverlayLayer("navisarthi-route", false))
        assertEquals("custom-road-name", MapOverlayOrder.geometryTop(layers))
        assertEquals("poi-text", MapOverlayOrder.routeLabelAnchor(layers))
    }

    @Test fun trafficCanAnchorAboveSatelliteRoadsWithoutCoveringLabels() {
        val layers = listOf(OverlayLayer("road", false), OverlayLayer("navisarthi-satellite", false),
            OverlayLayer("navisarthi-sat-roads", false), OverlayLayer("street-labels", true), OverlayLayer("navisarthi-route", false))
        assertEquals("navisarthi-sat-roads", MapOverlayOrder.geometryTop(layers, setOf("navisarthi-satellite", "navisarthi-sat-roads")))
        assertEquals("street-labels", MapOverlayOrder.routeLabelAnchor(layers))
    }

    @Test fun stylesWithoutLabelsAndEmptyStylesNeedNoNamedAnchor() {
        assertNull(MapOverlayOrder.routeLabelAnchor(listOf(OverlayLayer("road", false))))
        assertNull(MapOverlayOrder.geometryTop(emptyList()))
        assertNull(MapOverlayOrder.routeLabelAnchor(emptyList()))
    }
}
