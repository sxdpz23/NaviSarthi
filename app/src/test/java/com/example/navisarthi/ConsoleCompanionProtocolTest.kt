package com.example.navisarthi

import com.example.navisarthi.dash.protocol.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ConsoleCompanionProtocolTest {
    private fun payload(packet: ByteArray): ByteArray {
        val length = ((packet[19].toInt() and 255) shl 8) or (packet[20].toInt() and 255)
        assertEquals(packet.size - 21, length)
        assertEquals(packet.size, ((packet[0].toInt() and 255) shl 8) or (packet[1].toInt() and 255))
        return packet.copyOfRange(21, packet.size)
    }
    @Test fun zoomIconsFollowReferenceAtBothLimitsAndMiddle() {
        assertArrayEquals(byteArrayOf(0x30), payload(DashCommands.zoomAvailability(true, true)))
        assertArrayEquals(byteArrayOf(0x10), payload(DashCommands.zoomAvailability(true, false)))
        assertArrayEquals(byteArrayOf(0x20), payload(DashCommands.zoomAvailability(false, true)))
        assertArrayEquals(byteArrayOf(0), payload(DashCommands.zoomAvailability(false, false)))
    }
    @Test fun artReassemblesAcrossSmallAndExactChunkBoundaries() {
        listOf(1, 999, 1000, 1001, 1999, 2000, 2001, 65535).forEach { size ->
            val jpeg = ByteArray(size) { (it % 251).toByte() }
            val frames = DashCommands.albumArtFrames(jpeg).map(::payload)
            assertEquals(0, frames.first()[0].toInt())
            assertEquals(size, ((frames[0][1].toInt() and 255) shl 8) or (frames[0][2].toInt() and 255))
            assertEquals(255, frames.last()[0].toInt() and 255)
            frames.drop(1).dropLast(1).forEachIndexed { index, bytes -> assertEquals(index + 1, bytes[0].toInt() and 255) }
            val rebuilt = frames.mapIndexed { index, bytes -> bytes.drop(if (index == 0) 3 else 1) }.flatten().toByteArray()
            assertArrayEquals(jpeg, rebuilt)
            frames.forEach { assertTrue(it.size <= 1003) }
        }
    }
    @Test fun singleArtChunkHasSeparateEndMarker() {
        val frames = DashCommands.albumArtFrames(byteArrayOf(1, 2, 3))
        assertEquals(2, frames.size)
        assertArrayEquals(byteArrayOf(0, 0, 3, 1, 2, 3), payload(frames[0]))
        assertArrayEquals(byteArrayOf(-1), payload(frames[1]))
    }
    @Test fun categoryRequestsAreStrictAndExcludeDialSelectionEvents() {
        assertEquals(CallHistoryCategory.INCOMING, CallHistoryCategory.fromRequest(byteArrayOf(-86, -86)))
        assertEquals(CallHistoryCategory.MISSED, CallHistoryCategory.fromRequest(byteArrayOf(-86, -69)))
        assertEquals(CallHistoryCategory.OUTGOING, CallHistoryCategory.fromRequest(byteArrayOf(-86, -52)))
        assertNull(CallHistoryCategory.fromRequest(byteArrayOf(0, -86)))
        assertNull(CallHistoryCategory.fromRequest(byteArrayOf(-86)))
        assertNull(CallHistoryCategory.fromRequest(byteArrayOf(-86, 1)))
    }
    private fun decrypt(packet: ByteArray, key: ByteArray): ByteArray {
        val data = payload(packet)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(data.copyOfRange(0, 16)))
        return cipher.doFinal(data.copyOfRange(16, data.size))
    }
    @Test fun historySlotsUseSessionKeyFreshIvAndTerminatedReferenceText() {
        val key = ByteArray(32) { it.toByte() }
        val first = CallHistoryProtocol.slot(0, "Caller", key)
        val second = CallHistoryProtocol.slot(0, "Caller", key)
        assertFalse(payload(first).contentEquals(payload(second)))
        assertArrayEquals("Caller\u0000".toByteArray(), decrypt(first, key))
        assertArrayEquals("\u0000".toByteArray(), decrypt(CallHistoryProtocol.slot(4, "", key), key))
        assertEquals(0x20, CallHistoryProtocol.slot(4, "", key)[18].toInt())
        assertEquals(20, decrypt(CallHistoryProtocol.slot(2, "A".repeat(40), key), key).size)
        assertArrayEquals(byteArrayOf(-69), payload(CallHistoryProtocol.categoryReady(CallHistoryCategory.MISSED)))
    }
    @Test(expected = IllegalArgumentException::class) fun artRejectsEmptyPayload() { DashCommands.albumArtFrames(byteArrayOf()) }
    @Test(expected = IllegalArgumentException::class) fun historyRejectsInvalidSlot() { CallHistoryProtocol.slot(5, "", ByteArray(32)) }
}
