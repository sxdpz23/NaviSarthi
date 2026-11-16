package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.console.*
import com.sxdpz23.navisarthi.ui.theme.VehicleModel
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class VehicleConsoleTest {
    @Test fun pickerOrderAndExistingSavedIdentifiersAreStable() {
        assertEquals(listOf("Meteor350", "Himalayan450", "Guerrilla450", "Bear650", "Himalayan750"),
            VehicleModel.entries.map { it.name })
        assertEquals(ConsoleType.TRIPPER_POD, VehicleModel.Meteor350.consoleType)
        assertTrue(VehicleModel.entries.drop(1).all { it.consoleType == ConsoleType.TRIPPER_DASH })
        listOf("Himalayan450", "Guerrilla450", "Bear650", "Himalayan750").forEach {
            assertEquals(it, VehicleModel.valueOf(it).name)
        }
    }
    @Test fun meteorUsesRoseAndExistingThemesStayDistinct() {
        assertEquals(Color(0xFFE7A0B0), VehicleModel.Meteor350.accent)
        assertEquals(Color(0xFFFFC0CE), VehicleModel.Meteor350.bright)
        assertEquals(Color(0xFFAE667A), VehicleModel.Meteor350.deep)
        assertEquals(Color(0xFF241016), VehicleModel.Meteor350.onAccent)
        assertEquals(5, VehicleModel.entries.map { it.accent }.distinct().size)
        assertEquals(Color(0xFFFF793E), VehicleModel.Himalayan750.accent)
    }
    @Test fun eachProfileRequestsOnlyItsOwnTransportPermissions() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT"), VehicleModel.Meteor350.consoleType.navigationPermissions)
        VehicleModel.entries.drop(1).forEach {
            assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.NEARBY_WIFI_DEVICES"),
                it.consoleType.navigationPermissions)
        }
    }
    @Test fun switchesStopBeforePersistingAndActivatingInBothDirections() {
        for (old in VehicleModel.entries) for (next in VehicleModel.entries.filter { it != old }) {
            val events = mutableListOf<String>()
            var active: ConsoleType? = old.consoleType
            val coordinator = VehicleSessionCoordinator(old.name,
                stop = { events += "stop"; active = null },
                persist = { assertNull(active); events += it },
                activate = { assertNull(active); active = it; events += it.name })
            assertTrue(coordinator.select(next.name, next.consoleType))
            assertEquals(listOf("stop", next.name, next.consoleType.name), events)
            assertEquals(next.consoleType, active)
            events.clear()
            assertFalse(coordinator.select(next.name, next.consoleType))
            assertTrue(events.isEmpty())
        }
    }
    @Test fun callbacksFromRetiredSessionsCannotMutateNewSession() {
        val epoch = SessionEpoch()
        val pairing = epoch.next()
        assertTrue(epoch.current(pairing))
        epoch.next() // disconnect / vehicle switch
        val reconnect = epoch.next()
        assertFalse(epoch.current(pairing))
        assertTrue(epoch.current(reconnect))
        epoch.next()
        assertFalse(epoch.current(reconnect))
    }
}
