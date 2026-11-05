package com.example.navisarthi.media

/** Gate transport dispatches using monotonic time; failed sends do not consume a press. */
internal class MusicCommandGate(private val intervalMs: Long = 350) {
    private var lastSent: Long? = null
    fun allows(now: Long, supported: Long, requested: Long): Boolean =
        supported and requested != 0L && lastSent?.let { now - it < intervalMs } != true
    fun sent(now: Long) { lastSent = now }
}
