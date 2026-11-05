package com.example.navisarthi

import com.example.navisarthi.media.*
import org.junit.Assert.*
import org.junit.Test

class AccessoryBatteryStateTest {
    @Test fun unknownAndDisconnectedDevicesNeverShowAPercentage() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Helmet"))
        assertTrue(state.readings().isEmpty())
        state.report("b", 50)
        assertTrue(state.readings().isEmpty())
    }
    @Test fun zeroIsValidButUnknownAndInvalidLevelsClearTheReading() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Helmet"))
        state.report("a", 0)
        assertEquals(0, state.readings().single().percent)
        state.report("a", -1)
        assertTrue(state.readings().isEmpty())
        state.report("a", 101)
        assertTrue(state.readings().isEmpty())
    }
    @Test fun reportBeforeConnectionCallbackIsShownOnlyAfterConnection() {
        val state = AccessoryBatteryState()
        state.report("a", 85)
        assertTrue(state.readings().isEmpty())
        state.connections(mapOf("a" to "Helmet"))
        assertEquals(85, state.readings().single().percent)
    }
    @Test fun disconnectAndResetRemoveCachedLevels() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Helmet"))
        state.report("a", 50)
        state.disconnect("a")
        state.connections(mapOf("a" to "Helmet"))
        assertTrue(state.readings().isEmpty())
        state.report("a", 60)
        state.clear()
        assertTrue(state.readings().isEmpty())
    }
    @Test fun multipleAccessoriesRotateWithoutDroppingTheirPercentages() {
        val items = listOf(AccessoryBattery("a", "Helmet", 75), AccessoryBattery("b", "Earbuds", 40))
        assertEquals("BT Helmet 75%", accessoryBatteryLabel(items, 0))
        assertEquals("BT Earbuds 40%", accessoryBatteryLabel(items, 5000))
        assertEquals("BT Helmet 75%", accessoryBatteryLabel(items, 10000))
        assertNull(accessoryBatteryLabel(emptyList(), 0))
    }
    @Test fun iconModeKeepsPercentagesAndSelectsMatchingDeviceTypes() {
        val items = listOf(AccessoryBattery("a", "Helmet", 75, AccessoryIcon.HEADPHONES),
            AccessoryBattery("b", "Watch", 75, AccessoryIcon.WATCH))
        assertEquals("75%", accessoryBatteryLabel(items, 0, iconsOnly = true))
        assertEquals(AccessoryIcon.HEADPHONES, selectedAccessoryBattery(items, 0)?.icon)
        assertEquals(AccessoryIcon.WATCH, selectedAccessoryBattery(items, 5000)?.icon)
        assertEquals("BT Helmet 75%", accessoryBatteryLabel(items, 0, iconsOnly = false))
        assertNull(accessoryBatteryLabel(emptyList(), 0, iconsOnly = true))
    }
    @Test fun unknownDeviceClassHasGenericBluetoothIcon() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Accessory"))
        state.report("a", 50)
        assertEquals(AccessoryIcon.BLUETOOTH, state.readings().single().icon)
    }
    @Test fun mobileCyclesWithAccessoriesInNameAndIconModes() {
        val items = combinedBatteryReadings(80, listOf(AccessoryBattery("a", "Helmet", 60, AccessoryIcon.HEADPHONES)))
        assertEquals("Mobile 80%", accessoryBatteryLabel(items, 0))
        assertEquals("BT Helmet 60%", accessoryBatteryLabel(items, 5000))
        assertEquals("Mobile 80%", accessoryBatteryLabel(items, 10000))
        assertEquals("80%", accessoryBatteryLabel(items, 0, iconsOnly = true))
        assertEquals(AccessoryIcon.MOBILE, selectedAccessoryBattery(items, 0)?.icon)
        assertEquals(AccessoryIcon.HEADPHONES, selectedAccessoryBattery(items, 5000)?.icon)
    }
    @Test fun mobileRemainsWhenNoAccessoryIsConnected() {
        val items = combinedBatteryReadings(0, emptyList())
        assertEquals("Mobile 0%", accessoryBatteryLabel(items, 5000))
        assertTrue(combinedBatteryReadings(null, emptyList()).isEmpty())
    }
    @Test fun phonePercentageHonorsScaleAndRejectsMissingReadings() {
        assertEquals(50, mobileBatteryPercent(100, 200))
        assertEquals(100, mobileBatteryPercent(100, 100))
        assertNull(mobileBatteryPercent(-1, 100))
        assertNull(mobileBatteryPercent(101, 100))
        assertNull(mobileBatteryPercent(50, 0))
    }
    @Test fun lastAccessoryDisconnectLeavesOnlyMobileAcrossEveryCycle() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Helmet"))
        state.report("a", 60)
        assertEquals(2, combinedBatteryReadings(80, state.readings()).size)
        state.disconnect("a")
        state.connections(emptyMap())
        val items = combinedBatteryReadings(80, state.readings())
        assertEquals(1, items.size)
        listOf(0L, 5000L, 10000L, 60000L).forEach { time ->
            assertEquals("Mobile 80%", accessoryBatteryLabel(items, time))
            assertEquals("80%", accessoryBatteryLabel(items, time, iconsOnly = true))
            assertEquals(AccessoryIcon.MOBILE, selectedAccessoryBattery(items, time)?.icon)
        }
    }
    @Test fun bluetoothResetDoesNotRemovePhoneBattery() {
        val state = AccessoryBatteryState()
        state.connections(mapOf("a" to "Helmet"))
        state.report("a", 60)
        state.clear()
        assertEquals("Mobile 90%", accessoryBatteryLabel(combinedBatteryReadings(90, state.readings()), 5000))
    }
}
