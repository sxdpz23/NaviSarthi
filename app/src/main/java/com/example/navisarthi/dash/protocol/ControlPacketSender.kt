package com.example.navisarthi.dash.protocol

/** The sequence number and actual UDP write must have the same order across all callers. */
internal class ControlPacketSender(private val write: (ByteArray) -> Boolean) {
    private var sequence = 0
    @Synchronized fun send(packet: ByteArray): Boolean {
        val sequenced = K1GPacket.patchSeq(packet, sequence)
        sequence = (sequence + 1) and 255
        return write(sequenced)
    }
}
