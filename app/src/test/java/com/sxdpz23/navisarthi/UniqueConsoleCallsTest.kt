package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.media.*
import com.sxdpz23.navisarthi.dash.protocol.CallHistoryCategory
import org.junit.Assert.*
import org.junit.Test

class UniqueConsoleCallsTest {
    @Test fun repeatedNumberKeepsNewestNameAndOriginalDialNumber() {
        val rows = UniqueConsoleCalls()
        rows.add(ConsoleCallEntry("Newest", "+91 (98765) 43210"))
        rows.add(ConsoleCallEntry("Old name", "+91-98765-43210"))
        assertEquals(listOf(ConsoleCallEntry("Newest", "+91 (98765) 43210")), rows.entries())
    }
    @Test fun canonicalNumberMatchesDomesticInternationalAndFormattedVersions() {
        val rows = UniqueConsoleCalls()
        assertTrue(rows.add(ConsoleCallEntry("A", "9876543210"), "+919876543210"))
        assertFalse(rows.add(ConsoleCallEntry("A", "+919876543210")))
        assertFalse(rows.add(ConsoleCallEntry("A", "0091 98765 43210")))
        assertEquals(1, rows.entries().size)
    }
    @Test fun sameNameOrMatchingSuffixCannotMergeDifferentPhoneNumbers() {
        val rows = UniqueConsoleCalls()
        rows.add(ConsoleCallEntry("Shared name", "+919876543210"))
        rows.add(ConsoleCallEntry("Shared name", "+449876543210"))
        rows.add(ConsoleCallEntry("Shared name", "+919876543211"))
        assertEquals(3, rows.entries().size)
    }
    @Test fun scansPastFiveRawCallsAndStopsAfterFiveUniqueNumbers() {
        val rows = UniqueConsoleCalls()
        repeat(8) { rows.add(ConsoleCallEntry("Repeat", "12345")) }
        assertFalse(rows.full)
        (1..4).forEach { rows.add(ConsoleCallEntry("Other $it", "2345$it")) }
        assertTrue(rows.full)
        assertFalse(rows.add(ConsoleCallEntry("Too old", "34567")))
        assertEquals(listOf("12345", "23451", "23452", "23453", "23454"), rows.entries().map { it.number })
    }
    @Test fun unavailableNumbersDoNotConflateUnidentifiableCallers() {
        val rows = UniqueConsoleCalls()
        rows.add(ConsoleCallEntry("Private number", null))
        rows.add(ConsoleCallEntry("Private number", null))
        assertEquals(2, rows.entries().size)
        assertNull(consoleNumberKey("Invalid # number"))
    }
    @Test fun consoleDialingUsesTheSameDeduplicatedRowOrderAsTheDisplay() {
        val rows = UniqueConsoleCalls()
        rows.add(ConsoleCallEntry("First", "12345"))
        rows.add(ConsoleCallEntry("Duplicate", "12345"))
        rows.add(ConsoleCallEntry("Second", "23456"))
        val snapshot = ConsoleDialSnapshot()
        snapshot.displayed(CallHistoryCategory.OUTGOING, rows.entries())
        assertEquals("12345", snapshot.number(CallHistoryCategory.OUTGOING, 0, 0))
        assertEquals("23456", snapshot.number(CallHistoryCategory.OUTGOING, 1, 0))
        assertNull(snapshot.number(CallHistoryCategory.OUTGOING, 2, 0))
    }
}
