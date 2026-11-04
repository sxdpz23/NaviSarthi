package com.example.navisarthi

import com.example.navisarthi.dash.protocol.DashCommands
import org.junit.Assert.assertTrue
import org.junit.Test

class TripperGuidancePacketTest {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun idleRouteCardHidesBothGlyphs() {
        val packet = DashCommands.routeCard("NaviSarthi", true).hex()
        assertTrue(packet.contains("05020001ff"))
        assertTrue(packet.contains("05030001ff"))
    }

    @Test fun activeLeftTurnHidesOnlySecondaryGlyph() {
        val packet = DashCommands.activeNavPacket(maneuver = 0x14, primaryDist = 27).hex()
        assertTrue(packet.contains("0502000114"))
        assertTrue(packet.contains("05030001ff"))
        assertTrue(packet.contains("05040002001b"))
    }
}
