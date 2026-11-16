package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.map.FixFreshness
import com.sxdpz23.navisarthi.dash.video.RtpPacketizer
import org.junit.Assert.*
import org.junit.Test

class NavigationReliabilityTest {
    private val nowNs = 900_000_000_000L

    @Test fun delayedBatchCannotReviveTenMinuteOldPosition() {
        assertFalse(FixFreshness.usable(nowNs - 600_000_000_000L, nowNs, 5f))
        assertTrue(FixFreshness.usable(nowNs - 500_000_000L, nowNs, 5f))
    }

    @Test fun freshnessBoundaryAndMissingTimestamps() {
        assertTrue(FixFreshness.usable(nowNs - 4_000_000_000L, nowNs, 10f))
        assertFalse(FixFreshness.usable(nowNs - 4_001_000_000L, nowNs, 10f))
        assertEquals(Long.MAX_VALUE, FixFreshness.ageMs(0L, nowNs))
        assertEquals(Long.MAX_VALUE, FixFreshness.ageMs(nowNs + 1, nowNs))
    }

    @Test fun coarseAndInvalidAccuracyCannotDriveNavigation() {
        for (accuracy in listOf(200f, Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertFalse(FixFreshness.usable(nowNs, nowNs, accuracy))
        }
        assertTrue(FixFreshness.usable(nowNs, nowNs, 50f))
    }

    private fun timestamp(packet: ByteArray): Long = (4..7).fold(0L) { v, i ->
        (v shl 8) or (packet[i].toLong() and 255)
    }

    @Test fun stalledFrameTimelinePreservesRealElapsedTime() {
        val packets = mutableListOf<ByteArray>()
        val rtp = RtpPacketizer { packets.add(it) }
        val nal = byteArrayOf(0x61, 1, 2)
        rtp.packetize(nal, true, 0L)
        rtp.packetize(nal, true, 250L)
        rtp.packetize(nal, true, 2_250L)
        assertEquals(22_500L, (timestamp(packets[1]) - timestamp(packets[0])) and 0xffffffffL)
        assertEquals(180_000L, (timestamp(packets[2]) - timestamp(packets[1])) and 0xffffffffL)
    }

    @Test fun fragmentedKeyframeUsesOneTimestampAndOnlyFinalMarker() {
        val packets = mutableListOf<ByteArray>()
        RtpPacketizer { packets.add(it) }.packetize(ByteArray(5_000) { if (it == 0) 0x65 else 1 }, true, 500L)
        assertTrue(packets.size > 1)
        assertTrue(packets.all { timestamp(it) == timestamp(packets.first()) && it.size <= 1392 })
        assertTrue(packets.dropLast(1).all { it[1].toInt() and 0x80 == 0 })
        assertEquals(0x80, packets.last()[1].toInt() and 0x80)
    }
}
