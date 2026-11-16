package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.pod.*
import org.junit.Assert.*
import org.junit.Test

class PodWriteQueueTest {
    @Test fun nextPacketWaitsForWriteCompletionAndPreservesControlOrder() {
        val queue = PodWriteQueue()
        queue.offer(PodProtocol.handshake(false)); queue.offer(PodProtocol.pin("123456"))
        assertArrayEquals(PodProtocol.handshake(false), queue.next())
        assertNull(queue.next())
        queue.complete()
        assertArrayEquals(PodProtocol.pin("123456"), queue.next())
        assertNull(queue.next())
        queue.complete(); assertNull(queue.next())
    }
    @Test fun newestScreenReplacesStalePendingGuidanceButKeepsClockSync() {
        val queue = PodWriteQueue()
        queue.offer(PodProtocol.recalculating(false))
        queue.offer(PodProtocol.clock(12, 0, true))
        queue.offer(PodProtocol.recalculating(true))
        assertArrayEquals(PodProtocol.clock(12, 0, true), queue.next())
        queue.complete()
        assertArrayEquals(PodProtocol.recalculating(true), queue.next())
    }
    @Test fun stopPurgesPendingScreensAndDisconnectDropsAllWrites() {
        val queue = PodWriteQueue()
        queue.offer(PodProtocol.firmware()); queue.offer(PodProtocol.recalculating(false))
        queue.clearScreens()
        assertArrayEquals(PodProtocol.firmware(), queue.next())
        queue.complete(); assertNull(queue.next())
        queue.offer(PodProtocol.pin("123456")); queue.next()
        queue.offer(PodProtocol.clock(1, 2, true)); queue.clear()
        assertFalse(queue.writing); assertNull(queue.next())
    }
    @Test fun queuedPacketsOwnTheirBytes() {
        val queue = PodWriteQueue()
        val packet = PodProtocol.firmware()
        queue.offer(packet); packet.fill(0)
        assertArrayEquals(PodProtocol.firmware(), queue.next())
    }
}
