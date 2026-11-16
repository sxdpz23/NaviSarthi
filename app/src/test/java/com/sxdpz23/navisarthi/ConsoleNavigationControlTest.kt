package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ConsoleNavigationControlTest {
    @Test fun onlyExactStopCommandTriggersNavigationExit() {
        for (code in 0..255)
            assertEquals(code == 0x12, ConsoleNavigationControl.isExit(K1GPacket.tlv(9, 0, code)))
        assertFalse(ConsoleNavigationControl.isExit(K1GPacket.tlv(9, 0)))
        assertFalse(ConsoleNavigationControl.isExit(K1GPacket.tlv(9, 0, 0, 0x12)))
        assertFalse(ConsoleNavigationControl.isExit(K1GPacket.tlv(9, 1, 0x12)))
        assertFalse(ConsoleNavigationControl.isExit(K1GPacket.tlv(5, 0, 0x12)))
    }
    @Test fun decodedConsolePacketRecognizesStopAndAcknowledgesOriginalCommand() {
        val packet = "000d0001000000000900000112".hexToBytes()
        assertTrue(ConsoleNavigationControl.isExit(K1GPacket.parseIncoming(packet).single()))
        val ack = DashCommands.buttonAck(0x12.toByte())
        assertArrayEquals("0680000112".hexToBytes(), ack.copyOfRange(17, ack.size))
    }
}
