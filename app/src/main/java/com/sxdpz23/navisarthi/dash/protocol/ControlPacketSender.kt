package com.sxdpz23.navisarthi.dash.protocol

/** The sequence number and actual UDP write must have the same order across all callers. */
internal class ControlPacketSender(private val write: (ByteArray) -> Boolean) {
    private var sequence = 0
    @Synchronized fun send(packet: ByteArray): Boolean {
        val sequenced = K1GPacket.patchSeq(packet, sequence)
        sequence = (sequence + 1) and 255
        return write(sequenced)
    }

    /** Short media transaction: unrelated control packets cannot split an image. IO caller only. */
    @Synchronized fun sendBatch(packets: List<ByteArray>, current: () -> Boolean,
        pause: () -> Unit = { Thread.sleep(10) }): Int {
        var sent = 0
        for (packet in packets) {
            if (!current() || !send(packet)) break
            sent++
            if (sent < packets.size) pause()
        }
        return sent
    }
}
