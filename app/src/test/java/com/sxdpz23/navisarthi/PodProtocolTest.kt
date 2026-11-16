package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.pod.*
import com.sxdpz23.navisarthi.dash.nav.*
import org.junit.Assert.*
import org.junit.Test

class PodProtocolTest {
    private fun ByteArray.hex() = joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun maneuver(type: ManeuverType, exit: Int? = null) = Maneuver(type, "", GeoPoint(0.0, 0.0), 0.0, exit)

    @Test fun crcMatchesCcittFalseCheckVectorAndReferenceHandshakePackets() {
        assertEquals(0x29b1, PodProtocol.crc("123456789".toByteArray()))
        assertEquals("21010000000000000000000000000000000050A7", PodProtocol.handshake(false).hex())
        assertEquals("2100000000000000000000000000000000004045", PodProtocol.handshake(true).hex())
        assertEquals("03000000000000000000000000000000000045D9", PodProtocol.firmware().hex())
    }
    @Test fun pinUsesSixAsciiDigitsAndNeverAcceptsOtherInput() {
        val packet = PodProtocol.pin("012345")
        assertEquals("20303132333435", packet.copyOfRange(0, 7).hex())
        assertTrue(PodProtocol.valid(packet))
        listOf("", "12345", "1234567", "１２３４５６", "12a456").forEach { pin ->
            assertThrows(IllegalArgumentException::class.java) { PodProtocol.pin(pin) }
        }
    }
    @Test fun onlyValidAuthorizationReplyAuthorizesThePod() {
        assertEquals(PodProtocol.Authorization.ACCEPTED, PodProtocol.authorization(PodProtocol.packet(0x20, 1)))
        assertEquals(PodProtocol.Authorization.REJECTED, PodProtocol.authorization(PodProtocol.packet(0x20, 0)))
        assertNull(PodProtocol.authorization(PodProtocol.packet(0x01, 1)))
        assertNull(PodProtocol.authorization(PodProtocol.firmware()))
        assertNull(PodProtocol.authorization(ByteArray(2)))
        val corrupt = PodProtocol.packet(0x20, 1); corrupt[5] = 42
        assertFalse(PodProtocol.valid(corrupt))
        assertNull(PodProtocol.authorization(corrupt))
        assertFalse(PodProtocol.valid(PodProtocol.packet(0x20, 1) + 0))
    }
    @Test fun navigationEncodesTwoArrowsMetricDistancesAndNightFlag() {
        val packet = PodProtocol.guidance(maneuver(ManeuverType.TURN_LEFT), maneuver(ManeuverType.TURN_RIGHT),
            120.0, 22900.0, 600.0, true, PodBottomLine.DISTANCE, 12, 0)
        assertEquals("1011140078010115FFFF4100E50200000000", packet.copyOfRange(0, 18).hex())
        assertTrue(PodProtocol.valid(packet))
    }
    @Test fun arrivalAndRoundaboutUsePodSpecificGlyphs() {
        assertEquals(0, PodProtocol.icon(maneuver(ManeuverType.ARRIVE)))
        assertEquals(0x0d, PodProtocol.icon(maneuver(ManeuverType.ROUNDABOUT, 3)))
        assertEquals(0x0a, PodProtocol.icon(maneuver(ManeuverType.ROUNDABOUT, 12)))
        assertEquals(255, PodProtocol.icon(null))
    }
    @Test fun distanceBoundaryAndTimeFieldsAreBounded() {
        fun packet(meters: Double, bottom: PodBottomLine = PodBottomLine.TIME_REMAINING) =
            PodProtocol.guidance(null, null, meters, 1000.0, 3660.0, false, bottom, 23, 59)
        assertEquals("03E701", packet(999.0).copyOfRange(3, 6).hex())
        assertEquals("000A02", packet(1000.0).copyOfRange(3, 6).hex())
        assertEquals("010100", packet(1000.0).copyOfRange(11, 14).hex())
        assertEquals("173B00", packet(1000.0, PodBottomLine.ARRIVAL_TIME).copyOfRange(11, 14).hex())
        assertThrows(IllegalArgumentException::class.java) { packet(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { packet(-1.0) }
    }
    @Test fun clockEncodesMidnightNoonAnd24HourTime() {
        assertEquals("500000", PodProtocol.clock(0, 0, true).copyOfRange(0, 3).hex())
        assertEquals("504C00", PodProtocol.clock(0, 0, false).copyOfRange(0, 3).hex())
        assertEquals("508C00", PodProtocol.clock(12, 0, false).copyOfRange(0, 3).hex())
        assertEquals("50173B", PodProtocol.clock(23, 59, true).copyOfRange(0, 3).hex())
    }
}
