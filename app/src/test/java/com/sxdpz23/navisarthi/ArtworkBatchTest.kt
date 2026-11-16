package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ArtworkBatchTest {
    @Test fun unrelatedControlPacketsCannotSplitImageFragments() {
        val entered = CountDownLatch(1)
        val contender = CountDownLatch(1)
        val wire = mutableListOf<Int>()
        val sender = ControlPacketSender { wire += it[18].toInt() and 255; true }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val batch = workers.submit<Int> {
                sender.sendBatch(List(3) { K1GPacket.build(K1GPacket.tlv(5, 0x40, it)) }, { true }) {
                    entered.countDown()
                    check(contender.await(3, TimeUnit.SECONDS))
                }
            }
            val heartbeat = workers.submit {
                check(entered.await(3, TimeUnit.SECONDS))
                contender.countDown()
                sender.send(K1GPacket.build(K1GPacket.tlv(6, 1, 1)))
            }
            assertEquals(3, batch.get(5, TimeUnit.SECONDS))
            heartbeat.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(0x40, 0x40, 0x40, 1), wire)
        } finally { workers.shutdownNow() }
    }
    @Test fun identicalPlayerRefreshDoesNotInvalidateArtworkButRealChangesDo() {
        val a = NativeMusic("Song", "Album", "Artist", true, byteArrayOf(1, 2))
        assertTrue(sameMusicContent(a, a.copy(art = a.art!!.copyOf())))
        assertFalse(sameMusicContent(a, a.copy(title = "Next")))
        assertFalse(sameMusicContent(a, a.copy(art = byteArrayOf(3))))
        assertFalse(sameMusicContent(a, null))
    }
    @Test fun staleOrFailedBatchStopsBeforeRemainingFragments() {
        val packet = K1GPacket.build(K1GPacket.tlv(5, 0x40, 0))
        var calls = 0
        val sender = ControlPacketSender { calls++; calls != 2 }
        assertEquals(1, sender.sendBatch(List(3) { packet }, { true }, {}))
        assertEquals(2, calls)
        assertEquals(0, sender.sendBatch(listOf(packet), { false }, {}))
        assertEquals(2, calls)
    }
    @Test fun reported2258ByteArtworkReassemblesExactly() {
        val image = ByteArray(2258) { (it % 251).toByte() }
        val packets = DashCommands.albumArtFrames(image)
        assertEquals(listOf(1024, 1022, 280), packets.map { it.size })
        assertEquals(1, packets[1][21].toInt())
        assertEquals(255, packets.last()[21].toInt() and 255)
        assertArrayEquals(image, packets[0].drop(24).toByteArray() +
            packets[1].drop(22).toByteArray() + packets[2].drop(22).toByteArray())
    }
}
