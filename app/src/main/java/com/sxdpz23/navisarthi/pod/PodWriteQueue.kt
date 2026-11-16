package com.sxdpz23.navisarthi.pod

/** Single in-flight write; pending navigation frames always describe the newest fix. */
internal class PodWriteQueue {
    private val pending = ArrayDeque<ByteArray>()
    var writing: Boolean = false
        private set

    fun offer(packet: ByteArray) {
        require(PodProtocol.valid(packet))
        if (packet[0].toInt() == 0x10) clearScreens()
        if (pending.size < 12) pending.addLast(packet.copyOf())
    }
    fun next(): ByteArray? {
        if (writing || pending.isEmpty()) return null
        writing = true
        return pending.removeFirst()
    }
    fun complete() { writing = false }
    fun clearScreens() { pending.removeAll { it[0].toInt() == 0x10 } }
    fun clear() { pending.clear(); writing = false }
}
