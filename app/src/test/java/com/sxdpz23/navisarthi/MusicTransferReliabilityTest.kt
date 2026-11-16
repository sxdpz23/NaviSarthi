package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MusicTransferReliabilityTest {
    private val music = NativeMusic("Title", "Album", "Artist", true, byteArrayOf(1, 2, 3))

    @Test fun unchangedPlayerDoesNotReannounceResetFlagsEverySecond() {
        (1L..9L).forEach {
            assertFalse(musicTransferNeeded(music, music.copy(art = music.art!!.copyOf()), 0, it * 1000))
        }
        assertTrue(musicTransferNeeded(music, music, 0, 10000))
    }
    @Test fun trackPlaybackAndImageChangesEachRequireACompleteMediaRefresh() {
        assertTrue(musicTransferNeeded(null, music, 0, 0))
        assertTrue(musicTransferNeeded(music, music.copy(playing = false), 0, 1000))
        assertTrue(musicTransferNeeded(music, music.copy(title = "Next"), 0, 1000))
        assertTrue(musicTransferNeeded(music, music.copy(art = byteArrayOf(4)), 0, 1000))
        assertTrue(musicTransferNeeded(music, music.copy(art = null), 0, 1000))
    }
    @Test fun failedTransfersRemainPendingRatherThanWaitingForRefreshTimeout() {
        // Only successful batches become the previous snapshot in DashSession.
        assertTrue(musicTransferNeeded(null, music, 9000, 9500))
    }
    @Test fun concurrentControlTrafficKeepsSequenceAndWriteOrderIdenticalAcrossWrap() {
        val wire = mutableListOf<Int>()
        val sender = ControlPacketSender { packet -> wire += packet[16].toInt() and 255; true }
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(8)
        val tasks = (0 until 8).map {
            workers.submit {
                start.await()
                repeat(100) { sender.send(DashCommands.musicAvailable(true)) }
            }
        }
        try {
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals((0 until 800).map { it and 255 }, wire)
        } finally { workers.shutdownNow() }
    }
    @Test fun failedSocketWritesPropagateAndDoNotReuseSequenceNumbers() {
        val sequences = mutableListOf<Int>()
        val sender = ControlPacketSender { packet ->
            sequences += packet[16].toInt() and 255
            sequences.size != 1
        }
        assertFalse(sender.send(DashCommands.musicAvailable(true)))
        assertTrue(sender.send(DashCommands.musicAvailable(true)))
        assertEquals(listOf(0, 1), sequences)
    }
    @Test fun reported1423ByteImageMatchesReferenceFragmentSizesAndContent() {
        val jpeg = ByteArray(1423) { (it % 251).toByte() }
        val packets = DashCommands.albumArtFrames(jpeg)
        assertEquals(listOf(1024, 445), packets.map { it.size })
        assertArrayEquals(byteArrayOf(0, 5, 0x8F.toByte()), packets[0].copyOfRange(21, 24))
        assertEquals(255, packets[1][21].toInt() and 255)
        assertArrayEquals(jpeg, packets[0].copyOfRange(24, 1024) + packets[1].copyOfRange(22, 445))
    }
}
