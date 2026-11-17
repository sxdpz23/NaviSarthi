package com.sxdpz23.navisarthi.dash.map

import java.util.concurrent.atomic.AtomicBoolean

/** MapLibre resets its callback AFTER invoking it. Never start another snapshot inline. */
internal class DeferredMapRender(private val post: (() -> Unit) -> Unit, private val render: () -> Unit) {
    private val pending = AtomicBoolean(false)
    @Volatile private var closed = false

    fun request() {
        if (closed || !pending.compareAndSet(false, true)) return
        post {
            pending.set(false)
            if (!closed) render()
        }
    }

    fun close() { closed = true }
}
