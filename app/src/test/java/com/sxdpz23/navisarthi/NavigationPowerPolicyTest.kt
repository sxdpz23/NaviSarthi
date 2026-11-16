package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.nav.NavigationPowerPolicy
import org.junit.Assert.*
import org.junit.Test

class NavigationPowerPolicyTest {
    private fun NavigationPowerPolicy.profile(time: Long = 0, speed: Float = 10f, precise: Boolean = true,
        matched: Boolean = true, turn: Double? = 1000.0, recalculating: Boolean = false,
        interacting: Boolean = false, thermal: Int = 0, saver: Boolean = false) =
        select(time, speed, precise, matched, turn, recalculating, interacting, thermal, saver)

    @Test fun cruisingReducesFramesAndGpsWithoutDisablingPreciseLocation() {
        val profile = NavigationPowerPolicy().profile()
        assertEquals(500L, profile.frameMs)
        assertEquals(1000L, profile.locationMs)
        assertTrue(profile.prefetch)
    }
    @Test fun nearbyTurnAndHighSpeedApproachRetainResponsiveGuidance() {
        for (profile in listOf(NavigationPowerPolicy().profile(turn = 70.0),
            NavigationPowerPolicy().profile(speed = 30f, turn = 300.0))) {
            assertEquals(250L, profile.frameMs)
            assertEquals(500L, profile.locationMs)
        }
    }
    @Test fun uncertainFixWrongTurnAndInteractionsDoNotUseSlowGps() {
        val profiles = listOf(NavigationPowerPolicy().profile(precise = false),
            NavigationPowerPolicy().profile(matched = false),
            NavigationPowerPolicy().profile(recalculating = true),
            NavigationPowerPolicy().profile(interacting = true))
        profiles.forEach { assertEquals(250L, it.frameMs); assertEquals(500L, it.locationMs) }
    }
    @Test fun prolongedStopReducesUpdatesButMovingAgainRestoresFastProfile() {
        val policy = NavigationPowerPolicy()
        policy.profile(speed = 0f)
        assertEquals(1000L, policy.profile(time = 14999, speed = 0f).locationMs)
        val stopped = policy.profile(time = 15000, speed = 0f)
        assertEquals(2000L, stopped.locationMs)
        assertEquals(500L, stopped.frameMs)
        assertFalse(stopped.prefetch)
        assertEquals(250L, policy.profile(time = 16000, speed = 8f).frameMs)
        assertEquals(500L, policy.profile(time = 17000, speed = 8f).locationMs)
        assertEquals(1000L, policy.profile(time = 20000, speed = 8f).locationMs)
    }
    @Test fun thermalAndBatterySaverReduceOptionalWorkNotRecoveryAccuracy() {
        for (profile in listOf(NavigationPowerPolicy().profile(thermal = 2, precise = false),
            NavigationPowerPolicy().profile(saver = true, turn = 30.0))) {
            assertEquals(500L, profile.frameMs)
            assertEquals(500L, profile.locationMs)
            assertFalse(profile.prefetch)
        }
    }
    @Test fun unknownGpsDoesNotAccumulateStationarySavings() {
        val policy = NavigationPowerPolicy()
        policy.profile(speed = 0f)
        policy.profile(time = 10000, speed = 0f, precise = false)
        assertEquals(1000L, policy.profile(time = 16000, speed = 0f).locationMs)
        policy.reset()
        assertEquals(1000L, policy.profile(time = 20000, speed = 0f).locationMs)
    }
    @Test fun allProfilesStayInsideProvenConsoleCadenceAndFreshnessEnvelope() {
        for (thermal in 0..6) for (saver in listOf(false, true)) for (turn in listOf(5.0, 5000.0)) {
            val profile = NavigationPowerPolicy().profile(thermal = thermal, saver = saver, turn = turn)
            assertTrue(profile.frameMs in setOf(250L, 500L))
            assertTrue(profile.locationMs in 500L..2000L)
        }
    }
}
