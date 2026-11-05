package com.example.navisarthi

import com.example.navisarthi.media.MusicCommandGate
import org.junit.Assert.*
import org.junit.Test

class MusicCommandGateTest {
    @Test fun unsupportedActionsNeverDispatch() {
        val gate = MusicCommandGate()
        assertFalse(gate.allows(0, 0b001, 0b010))
        assertFalse(gate.allows(0, 0, 0b111))
        assertTrue(gate.allows(0, 0b001, 0b101))
    }
    @Test fun repeatedAndAlternatingCommandsShareDebounce() {
        val gate = MusicCommandGate()
        assertTrue(gate.allows(0, 0b111, 0b001))
        gate.sent(0)
        assertFalse(gate.allows(349, 0b111, 0b001))
        assertFalse(gate.allows(349, 0b111, 0b010))
        assertTrue(gate.allows(350, 0b111, 0b010))
    }
    @Test fun failedOrUnsupportedSendDoesNotConsumeNextPress() {
        val gate = MusicCommandGate()
        assertFalse(gate.allows(100, 1, 2))
        assertTrue(gate.allows(101, 1, 1))
        assertTrue(gate.allows(102, 1, 1))
    }
}
