package com.example.navisarthi

import com.example.navisarthi.dash.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CallHistoryTransferTest {
    private val key = ByteArray(32) { it.toByte() }
    private fun text(packet: ByteArray): String {
        val data = packet.copyOfRange(21, packet.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(data.copyOfRange(0, 16)))
        return cipher.doFinal(data.copyOfRange(16, data.size)).toString(Charsets.ISO_8859_1)
    }

    @Test fun uiCallerSendsAllFiveEncryptedSlotsThenCompletionOffCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        CallHistoryCategory.entries.forEach { category ->
            val packets = mutableListOf<ByteArray>()
            assertTrue(CallHistoryTransfer.send(category, listOf("Caller"), key, { true }, { true }) {
                assertNotSame("Android UI thread must never perform UDP sends", caller, Thread.currentThread())
                packets += it
                true
            })
            assertEquals(6, packets.size)
            (0..4).forEach { index ->
                assertEquals(0x1C + index, packets[index][18].toInt() and 255)
                assertEquals(if (index == 0) "Caller\u0000" else "\u0000", text(packets[index]))
            }
            assertArrayEquals(CallHistoryProtocol.categoryReady(category), packets.last())
        }
    }

    @Test fun failedSlotOrCompletionNeverReportsSuccessfulDelivery() = runBlocking {
        (1..6).forEach { failAt ->
            var calls = 0
            assertFalse(CallHistoryTransfer.send(CallHistoryCategory.MISSED, listOf("Caller"), key, { true }, { true }) {
                ++calls != failAt
            })
            assertEquals(failAt, calls)
        }
    }

    @Test fun disconnectedSessionStopsBeforeNextSlotOrCompletion() = runBlocking {
        listOf(0, 2, 5).forEach { stopAfter ->
            var calls = 0
            assertFalse(CallHistoryTransfer.send(CallHistoryCategory.INCOMING, listOf("Caller"), key,
                { true }, { calls < stopAfter }) { calls++; true })
            assertEquals(stopAfter, calls)
        }
    }

    @Test fun revokedAccessClearsRemainingSlots() = runBlocking {
        val packets = mutableListOf<ByteArray>()
        assertTrue(CallHistoryTransfer.send(CallHistoryCategory.FAVORITES, List(5) { "Caller" }, key,
            { packets.isEmpty() }, { true }) { packets += it; true })
        assertEquals("Caller\u0000", text(packets.first()))
        packets.drop(1).take(4).forEach { assertEquals("\u0000", text(it)) }
    }

    @Test fun cancelledTransferNeverSendsCompletion() = runBlocking {
        var sends = 0
        val transfer = launch {
            val transferJob = currentCoroutineContext()[Job]!!
            CallHistoryTransfer.send(CallHistoryCategory.OUTGOING, emptyList(), key, { true }, { true }) {
                sends++
                transferJob.cancel()
                true
            }
        }
        transfer.join()
        assertEquals(1, sends)
    }
}
