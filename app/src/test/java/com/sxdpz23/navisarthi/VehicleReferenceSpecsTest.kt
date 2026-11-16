package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.data.vehicleReferenceSpecs
import com.sxdpz23.navisarthi.ui.theme.VehicleModel
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class VehicleReferenceSpecsTest {
    @Test fun everySelectionHasItsOwnOfficialReference() {
        VehicleModel.entries.forEach { model ->
            val reference = vehicleReferenceSpecs(model)
            assertEquals(model, reference.vehicle)
            assertTrue(reference.rows.isNotEmpty())
            assertTrue(reference.sources.isNotEmpty())
            reference.sources.forEach { assertEquals("www.royalenfield.com", URI(it.url).host) }
        }
        assertEquals(5, VehicleModel.entries.map { vehicleReferenceSpecs(it).rows }.distinct().size)
    }

    @Test fun changingVehicleChangesCapacitiesAndPressure() {
        fun rows(model: VehicleModel) = vehicleReferenceSpecs(model).rows.toMap()
        assertEquals("11 L", rows(VehicleModel.Guerrilla450)["Fuel tank"])
        assertEquals("17 L", rows(VehicleModel.Himalayan450)["Fuel tank"])
        assertEquals("13.7 L", rows(VehicleModel.Bear650)["Fuel tank"])
        assertTrue(rows(VehicleModel.Bear650).getValue("Tyre pressure").contains("42 pillion"))
        assertTrue(rows(VehicleModel.Meteor350).getValue("Tyre pressure").contains("36 pillion"))
    }

    @Test fun unpublished750ServiceValuesNeverFallBackTo450() {
        val rows = vehicleReferenceSpecs(VehicleModel.Himalayan750).rows.toMap()
        assertEquals("17.8 L", rows["Fuel tank"])
        assertFalse(rows.containsKey("Battery"))
        assertFalse(rows.containsKey("Tyre pressure"))
        assertFalse(rows.containsKey("Fork oil"))
    }
}
