package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.core.data.ValhallaRouter
import com.sxdpz23.navisarthi.navigation.core.data.google.PolylineCodec
import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.core.model.TravelMode
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RoadTravelModeTest {
    @Test fun onlyCarAndMotorbikeAreSelectableAndOldModesNormalize() {
        assertEquals(listOf(TravelMode.DRIVE, TravelMode.MOTORBIKE), TravelMode.roadModes)
        assertEquals(TravelMode.MOTORBIKE, TravelMode.MOTORBIKE.roadMode())
        listOf(TravelMode.WALK, TravelMode.BICYCLE, TravelMode.TRANSIT).forEach {
            assertEquals(TravelMode.DRIVE, it.roadMode())
        }
    }

    @Test fun motorcycleRequestUsesMotorcycleCostingAndRealStopBreaks() {
        val points = listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0), LatLng(12.002, 77.0))
        val request = Json.parseToJsonElement(ValhallaRouter.motorcycleRequest(points, true, true, true, -10.0, true)).jsonObject
        assertEquals("motorcycle", request.getValue("costing").jsonPrimitive.content)
        assertFalse(request.containsKey("alternates")) // Multi-stop trip retains one ordered journey.
        val locations = request.getValue("locations").jsonArray
        assertEquals(3, locations.size)
        assertTrue(locations.all { it.jsonObject.getValue("type").jsonPrimitive.content == "break" })
        assertEquals(350.0, locations.first().jsonObject.getValue("heading").jsonPrimitive.double, 0.0)
        val options = request.getValue("costing_options").jsonObject.getValue("motorcycle").jsonObject
        assertEquals(0.0, options.getValue("use_tolls").jsonPrimitive.double, 0.0)
        assertEquals(0.0, options.getValue("use_highways").jsonPrimitive.double, 0.0)
        assertEquals(0.0, options.getValue("use_ferry").jsonPrimitive.double, 0.0)
    }

    @Test fun motorcycleResponseKeepsTheStopLegAndSharedGeometry() {
        val a = LatLng(12.0, 77.0); val b = LatLng(12.001, 77.0); val c = LatLng(12.002, 77.0)
        fun leg(from: LatLng, to: LatLng) = buildJsonObject {
            put("shape", PolylineCodec.encode(listOf(from, to), 6))
            putJsonArray("maneuvers") {
                add(buildJsonObject { put("type", 1); put("begin_shape_index", 0); put("length", .111); put("time", 10) })
                add(buildJsonObject { put("type", 4); put("begin_shape_index", 1); put("length", 0); put("time", 0) })
            }
        }
        val response = buildJsonObject { putJsonObject("trip") { putJsonArray("legs") { add(leg(a,b)); add(leg(b,c)) } } }
        val routes = ValhallaRouter.parseMotorcycleRoutes(response.toString(), false, false, false)
        val route = routes.single()
        assertEquals(2, route.legs.size)
        assertEquals(listOf(a,b,c), route.polyline)
        assertEquals(20.0, route.durationSeconds, .001)
        assertEquals(b, route.legs.first().maneuvers.last().location)
    }
}
