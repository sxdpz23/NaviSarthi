package com.example.navisarthi

import com.example.navisarthi.dash.protocol.*
import com.example.navisarthi.media.*
import org.junit.Assert.*
import org.junit.Test

class ConsoleCallingTest {
    @Test fun dialSelectionMatchesAllOriginalCategoriesAndSlots() {
        CallHistoryCategory.entries.forEach { category ->
            (0..4).forEach { index ->
                assertEquals(ConsoleDialSelection(category, index), CallHistoryProtocol.dialSelection(byteArrayOf(index.toByte(), category.code.toByte())))
            }
        }
        listOf(byteArrayOf(-86, -86), byteArrayOf(5, 0x55), byteArrayOf(0, 0), byteArrayOf(0), byteArrayOf(0, 0x55, 0))
            .forEach { assertNull(CallHistoryProtocol.dialSelection(it)) }
    }
    @Test fun missedCountUsesOnlyMissedCallTlvAndClampsToUnsigned16Bits() {
        fun payload(value: Int) = CallHistoryProtocol.missedCallCount(value).drop(17).toByteArray()
        assertArrayEquals(byteArrayOf(6, 10, 0, 2, 0, 0), payload(-1))
        assertArrayEquals(byteArrayOf(6, 10, 0, 2, 1, 2), payload(258))
        assertArrayEquals(byteArrayOf(6, 10, 0, 2, -1, -1), payload(70000))
    }
    @Test fun dialResolvesDisplayedRowsWithoutUsingNewProviderOrder() {
        val snapshot = ConsoleDialSnapshot()
        snapshot.displayed(CallHistoryCategory.INCOMING, listOf(ConsoleCallEntry("A", "+91 98765-43210"), ConsoleCallEntry("Private", null)))
        assertEquals("+919876543210", snapshot.number(CallHistoryCategory.INCOMING, 0, 0))
        assertNull(snapshot.number(CallHistoryCategory.OUTGOING, 0, 0))
        assertNull(snapshot.number(CallHistoryCategory.INCOMING, 1, 0))
        assertNull(snapshot.number(CallHistoryCategory.INCOMING, 4, 0))
        snapshot.clear()
        assertNull(snapshot.number(CallHistoryCategory.INCOMING, 0, 0))
    }
    @Test fun repeatedDialEventsAreBlockedAcrossCategoryChanges() {
        val snapshot = ConsoleDialSnapshot()
        snapshot.displayed(CallHistoryCategory.FAVORITES, listOf(ConsoleCallEntry("A", "12345")))
        snapshot.dialRequested(100)
        assertNull(snapshot.number(CallHistoryCategory.FAVORITES, 0, 101))
        snapshot.clear()
        snapshot.displayed(CallHistoryCategory.OUTGOING, listOf(ConsoleCallEntry("B", "98765")))
        assertNull(snapshot.number(CallHistoryCategory.OUTGOING, 0, 10099))
        assertEquals("98765", snapshot.number(CallHistoryCategory.OUTGOING, 0, 10100))
    }
    @Test fun phoneNumbersCannotInjectUriOrServiceCodeSyntax() {
        assertEquals("+919876543210", consoleDialNumber("+91 (98765) 43210"))
        listOf("", "Private", "tel:123", "123#", "*123#", "123;456", "123,456", "1+234", "123%23")
            .forEach { assertNull(consoleDialNumber(it)) }
    }
}
