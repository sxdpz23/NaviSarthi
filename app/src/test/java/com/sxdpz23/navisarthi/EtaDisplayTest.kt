package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.protocol.*
import org.junit.Assert.*
import org.junit.Test

class EtaDisplayTest {
    @Test fun ribbonShowsArrivalAndRemainingDurationInRequestedFormat() {
        assertEquals("ETA 18:45 | 1h 20m", etaRibbonText("18:45", 80, false))
        assertEquals("ETA 00:05 | 24m", etaRibbonText("00:05", 24, false))
        assertEquals("ETA 18:45 | 1h", etaRibbonText("18:45", 60, false))
        assertEquals("ETA 18:45 | <1m", etaRibbonText("18:45", 0, false))
    }

    @Test fun unavailableAndRecalculatingEstimatesDoNotInventArrivalTimes() {
        assertEquals("ETA --:-- | --m", etaRibbonText(null, null, false))
        assertEquals("Recalculating ETA", etaRibbonText("18:45", 80, true))
    }

    @Test fun etaCaptionUsesNativeRouteTitleWithoutLosingDistanceFields() {
        val caption = etaRibbonText("18:45", 80, false)
        val packet = DashCommands.routeCard(caption, true, totalDist = 123, totalUnit = 0x30)
        val titleLength = ((packet[19].toInt() and 255) shl 8) or (packet[20].toInt() and 255)
        assertEquals(caption + "\u0000", packet.copyOfRange(21, 21 + titleLength).toString(Charsets.UTF_8))
        val hex = packet.joinToString("") { "%02x".format(it.toInt() and 255) }
        assertTrue(hex.contains("05090002007b"))
        assertTrue(hex.contains("0546000130"))
    }
}
