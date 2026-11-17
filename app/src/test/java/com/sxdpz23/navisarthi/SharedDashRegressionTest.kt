package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.map.DeferredMapRender
import com.sxdpz23.navisarthi.dash.map.FixFreshness
import com.sxdpz23.navisarthi.navigation.core.location.NavPositionFilter
import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.ui.map.SnapshotStyleCache
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SharedDashRegressionTest {
    @Test fun slowSmoothedGpsStillPublishesEveryFreshFix() {
        var previous = LatLng(19.8, 72.7)
        val outliers = intArrayOf(0)
        repeat(30) { step ->
            val raw = LatLng(19.8 + (step + 1) * 0.00001, 72.7)
            val filtered = NavPositionFilter.filter(raw, previous, 1f, 1.0, outliers)
            assertNotEquals(raw, filtered) // reproduces the old coordinate-equality rejection
            assertTrue(NavPositionFilter.accepted(filtered, previous))
            previous = filtered
        }
    }

    @Test fun heldOutlierDoesNotReviveGpsButPersistentNewPositionRecovers() {
        val previous = LatLng(19.8, 72.7)
        val jump = LatLng(20.0, 72.7)
        val streak = intArrayOf(0)
        repeat(2) {
            val filtered = NavPositionFilter.filter(jump, previous, 0f, 1.0, streak)
            assertFalse(NavPositionFilter.accepted(filtered, previous))
        }
        assertTrue(NavPositionFilter.accepted(NavPositionFilter.filter(jump, previous, 0f, 1.0, streak), previous))
    }

    @Test fun parkedIdenticalCoordinatesAreStillFreshMeasurements() {
        val previous = LatLng(19.8, 72.7)
        val filtered = NavPositionFilter.filter(previous.copy(), previous, 0f, 1.0, intArrayOf(0))
        assertEquals(previous, filtered)
        assertTrue(NavPositionFilter.accepted(filtered, previous))
    }

    @Test fun powerSavingCadenceDoesNotDeclareGpsLostBetweenScheduledFixes() {
        val now = 900_000_000_000L
        val window = FixFreshness.windowMs(5_000)
        assertTrue(FixFreshness.usable(now - 5_500_000_000, now, 12f, window))
        assertFalse(FixFreshness.usable(now - 10_001_000_000, now, 12f, window))
        assertFalse(FixFreshness.usable(now, now, 500f, window))
        assertEquals(4_000L, FixFreshness.windowMs(500))
    }

    @Test fun themedFollowupAndNewStyleNeverRestartInsideNativeCallback() {
        val jobs = ArrayDeque<() -> Unit>()
        var insideCallback = false
        var themed = false
        var frames = 0
        lateinit var queue: DeferredMapRender
        queue = DeferredMapRender({ jobs.addLast(it) }) {
            check(!insideCallback) { "Snapshotter was already started" }
            insideCallback = true
            if (!themed) {
                themed = true
                queue.request() // first snapshot's palette pass needs a second frame
            } else frames++
            insideCallback = false // SDK clears the callback here, after returning
        }
        repeat(10) { queue.request() }
        assertEquals(1, jobs.size)
        jobs.removeFirst().invoke()
        assertEquals(0, frames)
        jobs.removeFirst().invoke()
        assertEquals(1, frames)
        themed = false // new black/dark/satellite style requires the same lifecycle
        queue.request()
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertEquals(2, frames)
        queue.request()
        queue.close()
        while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        assertEquals(2, frames)
    }

    @Test fun selectedStyleSurvivesConsoleWifiAndDoesNotReuseAnotherLook() {
        val directory = Files.createTempDirectory("nav-styles").toFile()
        try {
            val cache = SnapshotStyleCache(directory)
            for (look in listOf("liberty", "dark", "fiord")) {
                assertEquals(look, cache.load(look, "https://styles/$look", "https://styles/$look") { look })
            }
            for (look in listOf("liberty", "dark", "fiord")) {
                assertEquals(look, cache.load(look, "https://styles/$look", "https://styles/$look") { error("offline") })
            }
            assertNull(cache.load("bright", "https://styles/bright", "https://styles/bright") { null })
        } finally { directory.deleteRecursively() }
    }
}
